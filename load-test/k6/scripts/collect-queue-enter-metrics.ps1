param(
    [Parameter(Mandatory = $true)]
    [string]$SummaryPath,

    [Parameter(Mandatory = $true)]
    [string]$OutputPath,

    [string]$PrometheusUrl = 'http://127.0.0.1:9090',

    [int]$WindowSeconds = 60,

    [Nullable[int]]$EndOffsetSeconds = $null
)

$summary = Get-Content -Raw -Encoding utf8 $SummaryPath | ConvertFrom-Json
if ($null -eq $summary.setup_data -or $null -eq $summary.setup_data.openAtMillis) {
    throw 'k6 summary에 setup_data.openAtMillis가 없어 측정 시간 창을 계산할 수 없습니다.'
}

$openAtSeconds = [math]::Floor([double]$summary.setup_data.openAtMillis / 1000)
$endTimeSeconds = if ($null -ne $EndOffsetSeconds) {
    $openAtSeconds + $EndOffsetSeconds
} else {
    [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
}

$currentTimeSeconds = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
if ($endTimeSeconds -gt $currentTimeSeconds) {
    Start-Sleep -Seconds ($endTimeSeconds - $currentTimeSeconds + 1)
}

$window = "${WindowSeconds}s"
$queries = [ordered]@{
    'queueEnterP95' = "histogram_quantile(0.95, sum by (le) (increase(seat_rush_business_duration_seconds_bucket{mode=`"practice`",result=`"success`",operation=`"entry_token.issue`"}[$window])))"
    'entryTokenSignP95' = "histogram_quantile(0.95, sum by (le) (increase(seat_rush_business_duration_seconds_bucket{mode=`"practice`",result=`"success`",operation=`"entry_token.issue.sign`"}[$window])))"
    'entryTokenRedisP95' = "histogram_quantile(0.95, sum by (le) (increase(seat_rush_business_duration_seconds_bucket{mode=`"practice`",result=`"success`",operation=`"entry_token.issue.redis`"}[$window])))"
    'gatewayP95' = "histogram_quantile(0.95, sum by (le) (increase(seat_rush_gateway_duration_seconds_bucket{operation=`"queue.enter`",mode=`"practice`",status=~`"2..`"}[$window])))"
    'lettuceLuaAverageSeconds' = "sum(increase(lettuce_command_completion_seconds_sum{application=`"queue-service`",command=~`"EVAL|EVALSHA`"}[$window])) / sum(increase(lettuce_command_completion_seconds_count{application=`"queue-service`",command=~`"EVAL|EVALSHA`"}[$window]))"
    'lettuceLuaMaxSeconds' = "max_over_time(lettuce_command_completion_seconds_max{application=`"queue-service`",command=~`"EVAL|EVALSHA`"}[$window])"
    'lettuceLuaCount' = "sum(increase(lettuce_command_completion_seconds_count{application=`"queue-service`",command=~`"EVAL|EVALSHA`"}[$window]))"
    'redisLuaAverageSeconds' = "sum(increase(redis_commands_duration_seconds_total{redis=`"queue`",cmd=~`"eval|evalsha`"}[$window])) / sum(increase(redis_commands_total{redis=`"queue`",cmd=~`"eval|evalsha`"}[$window]))"
    'redisLuaCount' = "sum(increase(redis_commands_total{redis=`"queue`",cmd=~`"eval|evalsha`"}[$window]))"
    'jvmGcPauseSeconds' = "sum(increase(jvm_gc_pause_seconds_sum{application=`"queue-service`"}[$window]))"
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
    endOffsetSeconds = $EndOffsetSeconds
    results = $results
}

$outputDirectory = Split-Path -Parent $OutputPath
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
$output | ConvertTo-Json -Depth 20 | Set-Content -Encoding utf8 $OutputPath
