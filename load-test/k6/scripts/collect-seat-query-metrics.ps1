param(
    [Parameter(Mandatory = $true)]
    [string]$SummaryPath,

    [Parameter(Mandatory = $true)]
    [string]$OutputPath,

    [string]$PrometheusUrl = 'http://127.0.0.1:9090',

    [int]$WindowSeconds = 60
)

$summary = Get-Content -Raw -Encoding utf8 $SummaryPath | ConvertFrom-Json
if ($null -eq $summary.setup_data -or $null -eq $summary.setup_data.openAtMillis) {
    throw 'k6 summary에 setup_data.openAtMillis가 없어 측정 시간 창을 계산할 수 없습니다.'
}

$endTimeSeconds = [math]::Floor(([double]$summary.setup_data.openAtMillis / 1000) + $WindowSeconds)
$window = "${WindowSeconds}s"

# k6가 기록한 티켓 오픈 시각을 기준으로 같은 Prometheus 시간 창을 조회합니다.
$queries = [ordered]@{
    'seatQueryP95' = "histogram_quantile(0.95, sum by (le, operation) (increase(seat_rush_business_duration_seconds_bucket{mode=`"practice`",result=`"success`",operation=~`"seat\\.query.*`"}[$window])))"
    'ticketResponseP95' = "histogram_quantile(0.95, sum by (le, stage) (increase(seat_rush_response_duration_seconds_bucket{mode=`"practice`",status=~`"2..`"}[$window])))"
    'gatewayP95' = "histogram_quantile(0.95, sum by (le) (increase(seat_rush_gateway_duration_seconds_bucket{operation=`"seat.query`",mode=`"practice`",status=~`"2..`"}[$window])))"
    'hikariActiveMax' = "max_over_time(hikaricp_connections_active{application=`"ticket-service`"}[$window])"
    'hikariPendingMax' = "max_over_time(hikaricp_connections_pending{application=`"ticket-service`"}[$window])"
    'hikariMax' = "max_over_time(hikaricp_connections_max{application=`"ticket-service`"}[$window])"
    'jvmGcPauseSeconds' = "sum(increase(jvm_gc_pause_seconds_sum{application=`"ticket-service`"}[$window]))"
}

function Invoke-PrometheusQuery {
    param(
        [string]$Name,
        [string]$Query
    )

    $uri = "$PrometheusUrl/api/v1/query?query=$([uri]::EscapeDataString($Query))&time=$endTimeSeconds"
    $response = Invoke-RestMethod -Uri $uri -TimeoutSec 15

    if ($response.status -ne 'success') {
        throw "Prometheus 쿼리 실패: $Name"
    }

    return [pscustomobject]@{
        name = $Name
        query = $Query
        result = $response.data.result
    }
}

$results = foreach ($entry in $queries.GetEnumerator()) {
    Invoke-PrometheusQuery -Name $entry.Key -Query $entry.Value
}

$output = [pscustomobject]@{
    summaryPath = $SummaryPath
    practiceSessionId = $summary.setup_data.practiceSessionId
    openAtMillis = $summary.setup_data.openAtMillis
    windowSeconds = $WindowSeconds
    endTimeSeconds = $endTimeSeconds
    results = $results
}

$outputDirectory = Split-Path -Parent $OutputPath
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
$output | ConvertTo-Json -Depth 20 | Set-Content -Encoding utf8 $OutputPath
