param(
    [Parameter(Mandatory = $true)]
    [string]$SummaryPath
)

$summary = Get-Content -Raw -Encoding utf8 $SummaryPath | ConvertFrom-Json

# k6 실행 중에는 setup 데이터가 필요하지만, 저장하는 결과 파일에는 access token을 남기지 않습니다.
if ($null -ne $summary.setup_data) {
    $summary.setup_data.PSObject.Properties.Remove('accessTokens')
}

$summary | ConvertTo-Json -Depth 100 | Set-Content -Encoding utf8 $SummaryPath
