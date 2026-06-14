[CmdletBinding()]
param(
    [string]$GatewayUrl = "http://localhost:8080",
    [long]$ConcertId = 1,
    [long]$ScheduleId = 1,
    [int]$AsyncTimeoutSeconds = 30,
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

function Invoke-Api {
    param(
        [ValidateSet("GET", "POST", "PATCH", "DELETE")]
        [string]$Method,
        [string]$Path,
        [string]$AccessToken,
        [string]$EntryToken,
        $Body
    )

    $headers = @{}
    if ($AccessToken) {
        $headers.Authorization = "Bearer $AccessToken"
    }
    if ($EntryToken) {
        $headers["X-Entry-Token"] = $EntryToken
    }

    $params = @{
        Method = $Method
        Uri = "$GatewayUrl$Path"
        Headers = $headers
        ContentType = "application/json"
    }
    if ($null -ne $Body) {
        $params.Body = $Body | ConvertTo-Json -Depth 10 -Compress
    }

    try {
        return Invoke-RestMethod @params
    } catch {
        $responseBody = ""
        if ($_.Exception.Response) {
            $stream = $_.Exception.Response.GetResponseStream()
            if ($stream) {
                $reader = New-Object System.IO.StreamReader($stream)
                $responseBody = $reader.ReadToEnd()
            }
        }
        throw "$Method $Path failed. $responseBody"
    }
}

function Assert-HttpStatus {
    param(
        [string]$Path,
        [int]$ExpectedStatus
    )

    try {
        Invoke-WebRequest -UseBasicParsing -Uri "$GatewayUrl$Path" -ErrorAction Stop | Out-Null
        throw "Expected HTTP $ExpectedStatus but request succeeded: $Path"
    } catch {
        if (-not $_.Exception.Response) {
            throw
        }

        $actualStatus = [int]$_.Exception.Response.StatusCode
        Assert-True ($actualStatus -eq $ExpectedStatus) "Expected HTTP $ExpectedStatus but received $actualStatus for $Path"
    }
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

function Convert-ToLocalDateTime([datetime]$Value) {
    return $Value.ToString("yyyy-MM-ddTHH:mm:ss")
}

try {
    Write-Step "Checking service connectivity through the API Gateway"
    $concerts = Invoke-Api -Method GET -Path "/api/concerts?page=0&size=1"
    Assert-True $concerts.isSuccess "Concert API must be available"
    Assert-HttpStatus -Path "/api/users/me" -ExpectedStatus 401

    Write-Step "Creating an E2E user and logging in"
    $runId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $email = "e2e-$runId@seat-rush.local"
    $password = "E2e-test-password!"
    $null = Invoke-Api -Method POST -Path "/api/auth/signup" -Body @{
        email = $email
        password = $password
        name = "E2E User"
    }
    $login = Invoke-Api -Method POST -Path "/api/auth/login" -Body @{
        email = $email
        password = $password
    }
    $accessToken = $login.result.accessToken
    Assert-True (-not [string]::IsNullOrWhiteSpace($accessToken)) "Login must return an access token"

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

    Write-Step "Joining the queue and waiting until entry is allowed"
    $join = Wait-Until -Description "schedule synchronization to Queue Service" -Action {
        Invoke-Api -Method POST -Path "/api/schedules/$ScheduleId/queues/join" -AccessToken $accessToken
    } -Success {
        param($result)
        $result.isSuccess
    }
    Assert-True ($join.result.position -ge 1) "Queue position must be positive"

    $position = Wait-Until -Description "an enterable queue position" -Action {
        Invoke-Api -Method GET -Path "/api/schedules/$ScheduleId/queues/me" -AccessToken $accessToken
    } -Success {
        param($result)
        $result.result.status -eq "ENTERABLE"
    }
    Assert-True ($position.result.status -eq "ENTERABLE") "User must become enterable"

    Write-Step "Issuing an entry token"
    $entry = Invoke-Api -Method POST -Path "/api/schedules/$ScheduleId/queues/enter" -AccessToken $accessToken
    $entryToken = $entry.result.entryToken
    Assert-True (-not [string]::IsNullOrWhiteSpace($entryToken)) "Enter API must return an entry token"

    Write-Step "Holding an available seat"
    $sections = Invoke-Api -Method GET -Path "/api/schedules/$ScheduleId/sections" -AccessToken $accessToken -EntryToken $entryToken
    $section = $sections.result | Select-Object -First 1
    Assert-True ($null -ne $section) "At least one seat section is required"

    $seats = Invoke-Api -Method GET -Path "/api/schedules/$ScheduleId/seats?sectionId=$($section.sectionId)" -AccessToken $accessToken -EntryToken $entryToken
    $seat = $seats.result | Where-Object { $_.status -eq "AVAILABLE" } | Select-Object -First 1
    Assert-True ($null -ne $seat) "At least one available seat is required"

    $hold = Invoke-Api -Method POST -Path "/api/schedules/$ScheduleId/seats/hold" -AccessToken $accessToken -EntryToken $entryToken -Body @{
        seatIds = @($seat.seatId)
    }
    $holdId = $hold.result.holdId
    Assert-True (-not [string]::IsNullOrWhiteSpace($holdId)) "Seat hold must return a hold ID"

    Write-Step "Creating a reservation and requesting payment"
    $reservation = Invoke-Api -Method POST -Path "/api/reservations" -AccessToken $accessToken -EntryToken $entryToken -Body @{
        holdId = $holdId
    }
    $reservationId = $reservation.result.reservationId
    Assert-True ($reservation.result.status -eq "PENDING_PAYMENT") "New reservation must wait for payment"

    $paymentRequest = Invoke-Api -Method POST -Path "/api/reservations/$reservationId/payments" -AccessToken $accessToken
    $paymentId = $paymentRequest.result.paymentId
    Assert-True (-not [string]::IsNullOrWhiteSpace($paymentId)) "Payment request must return a payment ID"

    Write-Step "Completing the mock payment after Kafka delivery"
    $payment = Wait-Until -Description "payment request event consumption" -Action {
        Invoke-Api -Method POST -Path "/api/payments/$paymentId/complete" -AccessToken $accessToken -Body @{
            result = "SUCCESS"
        }
    } -Success {
        param($result)
        $result.result.status -eq "SUCCESS"
    }
    Assert-True ($payment.result.reservationId -eq $reservationId) "Payment must reference the reservation"

    $repeatedPayment = Invoke-Api -Method POST -Path "/api/payments/$paymentId/complete" -AccessToken $accessToken -Body @{
        result = "SUCCESS"
    }
    Assert-True ($repeatedPayment.result.status -eq "SUCCESS") "Repeated payment completion must be idempotent"

    Write-Step "Waiting for the payment result to confirm the reservation"
    $confirmed = Wait-Until -Description "payment result event consumption" -Action {
        Invoke-Api -Method GET -Path "/api/reservations/$reservationId" -AccessToken $accessToken
    } -Success {
        param($result)
        $result.result.status -eq "CONFIRMED"
    }
    Assert-True ($confirmed.result.status -eq "CONFIRMED") "Reservation must be confirmed"

    $finalSeats = Invoke-Api -Method GET -Path "/api/schedules/$ScheduleId/seats?sectionId=$($section.sectionId)" -AccessToken $accessToken -EntryToken $entryToken
    $reservedSeat = $finalSeats.result | Where-Object { $_.seatId -eq $seat.seatId } | Select-Object -First 1
    Assert-True ($reservedSeat.status -eq "RESERVED") "Paid seat must be reserved"

    Write-Host "`nE2E verification passed." -ForegroundColor Green
    Write-Host "reservationId=$reservationId paymentId=$paymentId seatId=$($seat.seatId)"
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
