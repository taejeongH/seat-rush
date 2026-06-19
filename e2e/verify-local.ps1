[CmdletBinding()]
param(
    [string]$GatewayUrl = "http://localhost:8080",
    [long]$ConcertId = 1,
    [long]$ScheduleId = 1,
    [int]$AsyncTimeoutSeconds = 30,
    [string]$NotificationRedisContainer = "seat-rush-local-notification-redis",
    [switch]$SkipNotificationCheck,
    [string]$AdminAccessToken = $env:E2E_ADMIN_ACCESS_TOKEN
)

$ErrorActionPreference = "Stop"
$script:ScheduleChanged = $false
$script:OriginalSchedule = $null

function Write-Step([string]$Message) {
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) {
        throw "Assertion failed: $Message"
    }
}

function Convert-ToJsonBody($Body) {
    if ($null -eq $Body) {
        return $null
    }
    return $Body | ConvertTo-Json -Depth 10 -Compress
}

function Convert-ResponseBody([string]$Body) {
    if ([string]::IsNullOrWhiteSpace($Body)) {
        return $null
    }

    try {
        return $Body | ConvertFrom-Json
    } catch {
        return $Body
    }
}

function Invoke-ApiRaw {
    param(
        [ValidateSet("GET", "POST", "PATCH", "DELETE")]
        [string]$Method,
        [string]$Path,
        [string]$AccessToken,
        [string]$EntryToken,
        $Body
    )

    $headers = @{}
    if (-not [string]::IsNullOrWhiteSpace($AccessToken)) {
        $headers.Authorization = "Bearer $AccessToken"
    }
    if (-not [string]::IsNullOrWhiteSpace($EntryToken)) {
        $headers["X-Entry-Token"] = $EntryToken
    }

    $params = @{
        Method = $Method
        Uri = "$GatewayUrl$Path"
        Headers = $headers
        ContentType = "application/json"
        UseBasicParsing = $true
    }

    $jsonBody = Convert-ToJsonBody $Body
    if ($null -ne $jsonBody) {
        $params.Body = $jsonBody
    }

    try {
        $response = Invoke-WebRequest @params
        return [pscustomobject]@{
            Success = $true
            StatusCode = [int]$response.StatusCode
            Body = Convert-ResponseBody $response.Content
            RawBody = $response.Content
        }
    } catch {
        $statusCode = 0
        $responseBody = ""

        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
            $stream = $_.Exception.Response.GetResponseStream()
            if ($stream) {
                $reader = New-Object System.IO.StreamReader($stream)
                $responseBody = $reader.ReadToEnd()
            }
        }

        return [pscustomobject]@{
            Success = $false
            StatusCode = $statusCode
            Body = Convert-ResponseBody $responseBody
            RawBody = $responseBody
        }
    }
}

function Invoke-Api {
    param(
        [ValidateSet("GET", "POST", "PATCH", "DELETE")]
        [string]$Method,
        [string]$Path,
        [string]$AccessToken,
        [string]$EntryToken,
        $Body
    )

    $response = Invoke-ApiRaw `
        -Method $Method `
        -Path $Path `
        -AccessToken $AccessToken `
        -EntryToken $EntryToken `
        -Body $Body

    if (-not $response.Success) {
        throw "$Method $Path failed. status=$($response.StatusCode), body=$($response.RawBody)"
    }

    if ($null -ne $response.Body -and $response.Body.PSObject.Properties.Name -contains "isSuccess") {
        Assert-True $response.Body.isSuccess "$Method $Path returned isSuccess=false. body=$($response.RawBody)"
    }

    return $response.Body
}

function Assert-HttpStatus {
    param(
        [ValidateSet("GET", "POST", "PATCH", "DELETE")]
        [string]$Method,
        [string]$Path,
        [int[]]$ExpectedStatus,
        [string]$AccessToken,
        [string]$EntryToken,
        $Body
    )

    $response = Invoke-ApiRaw `
        -Method $Method `
        -Path $Path `
        -AccessToken $AccessToken `
        -EntryToken $EntryToken `
        -Body $Body

    Assert-True ($ExpectedStatus -contains $response.StatusCode) `
        "Expected HTTP $($ExpectedStatus -join ',') but received $($response.StatusCode) for $Method $Path. body=$($response.RawBody)"

    return $response
}

function Wait-Until {
    param(
        [string]$Description,
        [scriptblock]$Action,
        [scriptblock]$Success
    )

    $deadline = (Get-Date).AddSeconds($AsyncTimeoutSeconds)
    $lastResult = $null
    $lastError = $null

    while ((Get-Date) -lt $deadline) {
        try {
            $lastResult = & $Action
            if (& $Success $lastResult) {
                return $lastResult
            }
        } catch {
            $lastError = $_
        }

        Start-Sleep -Milliseconds 500
    }

    if ($lastError) {
        throw "Timed out waiting for $Description. Last error: $lastError"
    }
    throw "Timed out waiting for $Description. Last result: $($lastResult | ConvertTo-Json -Depth 10 -Compress)"
}

function Get-CompletedNotificationCount {
    param(
        [string]$ContainerName
    )

    if ($SkipNotificationCheck) {
        return $null
    }

    try {
        $keys = @(docker exec $ContainerName redis-cli --raw --scan --pattern "notification:event:*" 2>$null)
        if ($LASTEXITCODE -ne 0) {
            return $null
        }

        $completedCount = 0
        foreach ($key in $keys) {
            if ([string]::IsNullOrWhiteSpace($key)) {
                continue
            }

            $value = docker exec $ContainerName redis-cli --raw GET $key 2>$null
            if ($LASTEXITCODE -eq 0 -and $value -eq "COMPLETED") {
                $completedCount++
            }
        }
        return $completedCount
    } catch {
        return $null
    }
}

function Convert-ToLocalDateTime([datetime]$Value) {
    return $Value.ToString("yyyy-MM-ddTHH:mm:ss")
}

function New-E2eUser {
    param(
        [string]$RunId,
        [string]$Label
    )

    $email = "e2e-$RunId-$Label@seat-rush.local"
    $password = "E2e-test-password!"

    $null = Invoke-Api -Method POST -Path "/api/auth/signup" -Body @{
        email = $email
        password = $password
        name = "E2E $Label"
    }

    $login = Invoke-Api -Method POST -Path "/api/auth/login" -Body @{
        email = $email
        password = $password
    }

    Assert-True (-not [string]::IsNullOrWhiteSpace($login.result.accessToken)) "Login must return an access token for $Label"

    return [pscustomobject]@{
        Email = $email
        AccessToken = $login.result.accessToken
    }
}

function Enter-Queue {
    param(
        [long]$TargetScheduleId,
        [string]$AccessToken
    )

    $join = Wait-Until -Description "schedule synchronization to Queue Service" -Action {
        Invoke-Api -Method POST -Path "/api/schedules/$TargetScheduleId/queues/join" -AccessToken $AccessToken
    } -Success {
        param($result)
        $result.isSuccess
    }
    Assert-True ($join.result.position -ge 1) "Queue position must be positive"

    $position = Wait-Until -Description "an enterable queue position" -Action {
        Invoke-Api -Method GET -Path "/api/schedules/$TargetScheduleId/queues/me" -AccessToken $AccessToken
    } -Success {
        param($result)
        $result.result.status -eq "ENTERABLE"
    }
    Assert-True ($position.result.status -eq "ENTERABLE") "User must become enterable"

    $entry = Invoke-Api -Method POST -Path "/api/schedules/$TargetScheduleId/queues/enter" -AccessToken $AccessToken
    Assert-True (-not [string]::IsNullOrWhiteSpace($entry.result.entryToken)) "Enter API must return an entry token"

    return [pscustomobject]@{
        Join = $join
        Position = $position
        EntryToken = $entry.result.entryToken
    }
}

function Select-AvailableSeat {
    param(
        [long]$TargetScheduleId,
        [string]$AccessToken,
        [string]$EntryToken,
        [long[]]$ExcludedSeatIds = @()
    )

    $sections = Invoke-Api `
        -Method GET `
        -Path "/api/schedules/$TargetScheduleId/sections" `
        -AccessToken $AccessToken `
        -EntryToken $EntryToken

    $section = $sections.result | Select-Object -First 1
    Assert-True ($null -ne $section) "At least one seat section is required"

    $seats = Invoke-Api `
        -Method GET `
        -Path "/api/schedules/$TargetScheduleId/seats?sectionId=$($section.sectionId)" `
        -AccessToken $AccessToken `
        -EntryToken $EntryToken

    $seat = $seats.result |
        Where-Object { $_.status -eq "AVAILABLE" -and ($ExcludedSeatIds -notcontains [long]$_.seatId) } |
        Select-Object -First 1

    Assert-True ($null -ne $seat) "At least one available seat is required"

    return [pscustomobject]@{
        Section = $section
        Seat = $seat
    }
}

function Start-SeatHoldJob {
    param(
        [string]$Owner,
        [string]$TargetGatewayUrl,
        [long]$TargetScheduleId,
        [string]$AccessToken,
        [string]$EntryToken,
        [long]$SeatId
    )

    Start-Job -ArgumentList $Owner, $TargetGatewayUrl, $TargetScheduleId, $AccessToken, $EntryToken, $SeatId -ScriptBlock {
        param($Owner, $TargetGatewayUrl, $TargetScheduleId, $AccessToken, $EntryToken, $SeatId)

        $headers = @{
            Authorization = "Bearer $AccessToken"
            "X-Entry-Token" = $EntryToken
        }
        $body = @{ seatIds = @($SeatId) } | ConvertTo-Json -Depth 10 -Compress

        try {
            $response = Invoke-WebRequest `
                -UseBasicParsing `
                -Method POST `
                -Uri "$TargetGatewayUrl/api/schedules/$TargetScheduleId/seats/hold" `
                -Headers $headers `
                -ContentType "application/json" `
                -Body $body

            [pscustomobject]@{
                Owner = $Owner
                Success = $true
                StatusCode = [int]$response.StatusCode
                Body = $response.Content
            }
        } catch {
            $statusCode = 0
            $responseBody = ""
            if ($_.Exception.Response) {
                $statusCode = [int]$_.Exception.Response.StatusCode
                $stream = $_.Exception.Response.GetResponseStream()
                if ($stream) {
                    $reader = New-Object System.IO.StreamReader($stream)
                    $responseBody = $reader.ReadToEnd()
                }
            }

            [pscustomobject]@{
                Owner = $Owner
                Success = $false
                StatusCode = $statusCode
                Body = $responseBody
            }
        }
    }
}

try {
    Write-Step "Checking API Gateway connectivity and authentication guard"
    $concerts = Invoke-Api -Method GET -Path "/api/concerts?page=0&size=1"
    Assert-True $concerts.isSuccess "Concert API must be available"
    $null = Assert-HttpStatus -Method GET -Path "/api/users/me" -ExpectedStatus @(401, 403)

    Write-Step "Creating E2E test users"
    $runId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString()
    $primaryUser = New-E2eUser -RunId $runId -Label "primary"
    $rivalUserA = New-E2eUser -RunId $runId -Label "rival-a"
    $rivalUserB = New-E2eUser -RunId $runId -Label "rival-b"

    $initialNotificationCount = Get-CompletedNotificationCount -ContainerName $NotificationRedisContainer
    if ($null -eq $initialNotificationCount -and -not $SkipNotificationCheck) {
        Write-Warning "Notification Redis container was not available. Notification completion check will be skipped."
    }

    Write-Step "Temporarily opening schedule $ScheduleId"
    Assert-True (-not [string]::IsNullOrWhiteSpace($AdminAccessToken)) "Set E2E_ADMIN_ACCESS_TOKEN to an access token issued to an ADMIN user"

    $schedules = Invoke-Api -Method GET -Path "/api/concerts/$ConcertId/schedules"
    $script:OriginalSchedule = $schedules.result | Where-Object { $_.scheduleId -eq $ScheduleId } | Select-Object -First 1
    Assert-True ($null -ne $script:OriginalSchedule) "Schedule $ScheduleId must belong to concert $ConcertId"
    Assert-True ($script:OriginalSchedule.status -ne "CANCELED") "Canceled schedules cannot be used for E2E verification"

    $now = Get-Date
    $null = Invoke-Api -Method PATCH -Path "/api/admin/schedules/$ScheduleId" -AccessToken $AdminAccessToken -Body @{
        performanceAt = Convert-ToLocalDateTime $now.AddHours(3)
        bookingOpenAt = Convert-ToLocalDateTime $now.AddMinutes(-1)
        bookingCloseAt = Convert-ToLocalDateTime $now.AddHours(2)
        status = "BOOKING_OPEN"
    }
    $script:ScheduleChanged = $true

    Write-Step "Verifying queue join and duplicate queue join"
    $primaryJoin = Wait-Until -Description "schedule synchronization to Queue Service" -Action {
        Invoke-Api -Method POST -Path "/api/schedules/$ScheduleId/queues/join" -AccessToken $primaryUser.AccessToken
    } -Success {
        param($result)
        $result.isSuccess
    }
    Assert-True ($primaryJoin.result.position -ge 1) "Queue position must be positive"

    $duplicateJoin = Invoke-Api -Method POST -Path "/api/schedules/$ScheduleId/queues/join" -AccessToken $primaryUser.AccessToken
    Assert-True $duplicateJoin.result.alreadyJoined "Duplicate queue join must be marked as already joined"
    Assert-True ($duplicateJoin.result.position -eq $primaryJoin.result.position) "Duplicate queue join must keep the same position"

    $primaryPosition = Wait-Until -Description "an enterable queue position" -Action {
        Invoke-Api -Method GET -Path "/api/schedules/$ScheduleId/queues/me" -AccessToken $primaryUser.AccessToken
    } -Success {
        param($result)
        $result.result.status -eq "ENTERABLE"
    }
    $primaryEntry = Invoke-Api -Method POST -Path "/api/schedules/$ScheduleId/queues/enter" -AccessToken $primaryUser.AccessToken
    Assert-True (-not [string]::IsNullOrWhiteSpace($primaryEntry.result.entryToken)) "Enter API must return an entry token"
    $primaryQueue = [pscustomobject]@{
        Join = $primaryJoin
        Position = $primaryPosition
        EntryToken = $primaryEntry.result.entryToken
    }

    Write-Step "Verifying concurrent hold requests for the same seat"
    $rivalQueueA = Enter-Queue -TargetScheduleId $ScheduleId -AccessToken $rivalUserA.AccessToken
    $rivalQueueB = Enter-Queue -TargetScheduleId $ScheduleId -AccessToken $rivalUserB.AccessToken
    $raceSeatSelection = Select-AvailableSeat `
        -TargetScheduleId $ScheduleId `
        -AccessToken $rivalUserA.AccessToken `
        -EntryToken $rivalQueueA.EntryToken
    $raceSeatId = [long]$raceSeatSelection.Seat.seatId

    $jobs = @(
        Start-SeatHoldJob -Owner "rival-a" -TargetGatewayUrl $GatewayUrl -TargetScheduleId $ScheduleId -AccessToken $rivalUserA.AccessToken -EntryToken $rivalQueueA.EntryToken -SeatId $raceSeatId
        Start-SeatHoldJob -Owner "rival-b" -TargetGatewayUrl $GatewayUrl -TargetScheduleId $ScheduleId -AccessToken $rivalUserB.AccessToken -EntryToken $rivalQueueB.EntryToken -SeatId $raceSeatId
    )

    Wait-Job -Job $jobs | Out-Null
    $raceResults = $jobs | Receive-Job
    $jobs | Remove-Job

    $successCount = @($raceResults | Where-Object { $_.Success }).Count
    $failureCount = @($raceResults | Where-Object { -not $_.Success }).Count
    if ($successCount -ne 1 -or $failureCount -ne 1) {
        Write-Host "Concurrent hold results:" -ForegroundColor Yellow
        $raceResults | ForEach-Object {
            Write-Host "owner=$($_.Owner), success=$($_.Success), status=$($_.StatusCode), body=$($_.Body)" -ForegroundColor Yellow
        }
    }
    Assert-True ($successCount -eq 1) "Only one concurrent hold request must succeed"
    Assert-True ($failureCount -eq 1) "One concurrent hold request must fail"

    Write-Step "Verifying seat hold, duplicate reservation prevention, and payment request"
    $seatSelection = Select-AvailableSeat `
        -TargetScheduleId $ScheduleId `
        -AccessToken $primaryUser.AccessToken `
        -EntryToken $primaryQueue.EntryToken `
        -ExcludedSeatIds @($raceSeatId)
    $seatId = [long]$seatSelection.Seat.seatId

    $hold = Invoke-Api -Method POST -Path "/api/schedules/$ScheduleId/seats/hold" -AccessToken $primaryUser.AccessToken -EntryToken $primaryQueue.EntryToken -Body @{
        seatIds = @($seatId)
    }
    $holdId = $hold.result.holdId
    Assert-True (-not [string]::IsNullOrWhiteSpace($holdId)) "Seat hold must return a hold ID"

    $reservation = Invoke-Api -Method POST -Path "/api/reservations" -AccessToken $primaryUser.AccessToken -EntryToken $primaryQueue.EntryToken -Body @{
        holdId = $holdId
    }
    $reservationId = $reservation.result.reservationId
    Assert-True ($reservation.result.status -eq "PENDING_PAYMENT") "New reservation must wait for payment"

    $duplicateReservation = Assert-HttpStatus `
        -Method POST `
        -Path "/api/reservations" `
        -ExpectedStatus @(400, 409) `
        -AccessToken $primaryUser.AccessToken `
        -EntryToken $primaryQueue.EntryToken `
        -Body @{ holdId = $holdId }
    Assert-True (-not $duplicateReservation.Success) "Duplicate holdId reservation must fail"

    $paymentRequest = Invoke-Api -Method POST -Path "/api/reservations/$reservationId/payments" -AccessToken $primaryUser.AccessToken
    $paymentId = $paymentRequest.result.paymentId
    Assert-True (-not [string]::IsNullOrWhiteSpace($paymentId)) "Payment request must return a payment ID"

    Write-Step "Verifying mock payment completion and idempotent repeated completion"
    $payment = Wait-Until -Description "payment request event consumption" -Action {
        Invoke-Api -Method POST -Path "/api/payments/$paymentId/complete" -AccessToken $primaryUser.AccessToken -Body @{
            result = "SUCCESS"
        }
    } -Success {
        param($result)
        $result.result.status -eq "SUCCESS"
    }
    Assert-True ($payment.result.reservationId -eq $reservationId) "Payment must reference the reservation"

    $repeatedPayment = Invoke-Api -Method POST -Path "/api/payments/$paymentId/complete" -AccessToken $primaryUser.AccessToken -Body @{
        result = "SUCCESS"
    }
    Assert-True ($repeatedPayment.result.status -eq "SUCCESS") "Repeated payment completion must be idempotent"

    Write-Step "Verifying reservation confirmation and reserved seat after Kafka payment result"
    $confirmed = Wait-Until -Description "payment result event consumption" -Action {
        Invoke-Api -Method GET -Path "/api/reservations/$reservationId" -AccessToken $primaryUser.AccessToken
    } -Success {
        param($result)
        $result.result.status -eq "CONFIRMED"
    }
    Assert-True ($confirmed.result.status -eq "CONFIRMED") "Reservation must be confirmed"

    $finalSeats = Invoke-Api `
        -Method GET `
        -Path "/api/schedules/$ScheduleId/seats?sectionId=$($seatSelection.Section.sectionId)" `
        -AccessToken $primaryUser.AccessToken `
        -EntryToken $primaryQueue.EntryToken
    $reservedSeat = $finalSeats.result | Where-Object { [long]$_.seatId -eq $seatId } | Select-Object -First 1
    Assert-True ($reservedSeat.status -eq "RESERVED") "Paid seat must be reserved"

    if ($null -ne $initialNotificationCount) {
        Write-Step "Verifying notification event consumption"
        $completedNotificationCount = Wait-Until -Description "notification event consumption" -Action {
            Get-CompletedNotificationCount -ContainerName $NotificationRedisContainer
        } -Success {
            param($result)
            $null -ne $result -and $result -gt $initialNotificationCount
        }
        Assert-True ($completedNotificationCount -gt $initialNotificationCount) "Notification consumer must complete at least one new notification event"
    }

    Write-Host "`nE2E verification passed." -ForegroundColor Green
    Write-Host "reservationId=$reservationId paymentId=$paymentId seatId=$seatId raceSeatId=$raceSeatId"
} finally {
    if ($script:ScheduleChanged -and $script:OriginalSchedule -and $AdminAccessToken) {
        Write-Step "Restoring the original schedule"
        try {
            $null = Invoke-Api -Method PATCH -Path "/api/admin/schedules/$ScheduleId" -AccessToken $AdminAccessToken -Body @{
                performanceAt = $script:OriginalSchedule.performanceAt
                bookingOpenAt = $script:OriginalSchedule.bookingOpenAt
                bookingCloseAt = $script:OriginalSchedule.bookingCloseAt
                status = $script:OriginalSchedule.status
            }
        } catch {
            Write-Warning "Failed to restore schedule $ScheduleId. Restore it manually: $_"
        }
    }
}
