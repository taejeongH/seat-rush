param(
    [Parameter(Mandatory = $true)]
    [string]$ResultDirectory,

    [Parameter(Mandatory = $true)]
    [datetimeoffset]$From,

    [Parameter(Mandatory = $true)]
    [datetimeoffset]$To,

    [string]$PrometheusUrl = 'http://127.0.0.1:9090'
)

Add-Type -AssemblyName System.Drawing

$assetsDirectory = Join-Path $ResultDirectory 'assets'
New-Item -ItemType Directory -Path $assetsDirectory -Force | Out-Null

function Get-PrometheusRangeQuery {
    param([string]$Query)

    $encodedQuery = [uri]::EscapeDataString($Query)
    $start = $From.ToUnixTimeSeconds()
    $end = $To.ToUnixTimeSeconds()
    $uri = "$PrometheusUrl/api/v1/query_range?query=$encodedQuery&start=$start&end=$end&step=5"
    $response = Invoke-RestMethod -Uri $uri -Method Get

    if ($response.status -ne 'success') {
        throw "Prometheus query failed: $Query"
    }
    return $response.data.result
}

function Get-SeriesLabel {
    param($Series, [string]$Fallback)

    if ($Series.metric.application) {
        return $Series.metric.application
    }
    if ($Series.metric.instance) {
        return $Series.metric.instance
    }
    return $Fallback
}

function New-LineChart {
    param(
        [string]$FileName,
        [string]$Title,
        [string]$Unit,
        $Series
    )

    $width = 920
    $height = 430
    $left = 70
    $right = 220
    $top = 95
    $bottom = 60
    $plotWidth = $width - $left - $right
    $plotHeight = $height - $top - $bottom
    $bitmap = [System.Drawing.Bitmap]::new($width, $height)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.Clear([System.Drawing.Color]::White)

    $titleFont = [System.Drawing.Font]::new('Arial', 22, [System.Drawing.FontStyle]::Bold)
    $subtitleFont = [System.Drawing.Font]::new('Arial', 11)
    $axisFont = [System.Drawing.Font]::new('Arial', 10)
    $legendFont = [System.Drawing.Font]::new('Arial', 10)
    $textBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(25, 35, 45))
    $mutedBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(90, 105, 120))
    $gridPen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(225, 230, 235), 1)
    $axisPen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(130, 145, 160), 1)
    $colors = @(
        [System.Drawing.Color]::FromArgb(37, 99, 235),
        [System.Drawing.Color]::FromArgb(220, 38, 38),
        [System.Drawing.Color]::FromArgb(22, 163, 74),
        [System.Drawing.Color]::FromArgb(147, 51, 234),
        [System.Drawing.Color]::FromArgb(234, 88, 12),
        [System.Drawing.Color]::FromArgb(8, 145, 178)
    )

    $points = @($Series | ForEach-Object { $_.values } | ForEach-Object { $_ })
    $maxValue = if ($points.Count -gt 0) {
        [math]::Max(1, ($points | ForEach-Object { [double]$_[1] } | Measure-Object -Maximum).Maximum)
    } else {
        1
    }
    $maxValue *= 1.1

    $graphics.DrawString($Title, $titleFont, $textBrush, 28, 22)
    $rangeText = '{0} ~ {1} KST' -f $From.ToOffset([TimeSpan]::FromHours(9)).ToString('yyyy-MM-dd HH:mm:ss'), $To.ToOffset([TimeSpan]::FromHours(9)).ToString('HH:mm:ss')
    $graphics.DrawString($rangeText, $subtitleFont, $mutedBrush, 30, 55)
    $graphics.DrawString($Unit, $axisFont, $mutedBrush, $left, $top - 24)

    for ($index = 0; $index -le 4; $index++) {
        $ratio = $index / 4.0
        $y = $top + $plotHeight - ($ratio * $plotHeight)
        $value = $maxValue * $ratio
        $graphics.DrawLine($gridPen, $left, $y, $left + $plotWidth, $y)
        $graphics.DrawString(('{0:N2}' -f $value), $axisFont, $mutedBrush, 8, $y - 8)
    }

    for ($index = 0; $index -le 3; $index++) {
        $ratio = $index / 3.0
        $x = $left + ($ratio * $plotWidth)
        $graphics.DrawLine($gridPen, $x, $top, $x, $top + $plotHeight)
        $timestamp = $From.AddSeconds(($To - $From).TotalSeconds * $ratio).ToOffset([TimeSpan]::FromHours(9))
        $graphics.DrawString($timestamp.ToString('HH:mm:ss'), $axisFont, $mutedBrush, $x - 24, $top + $plotHeight + 12)
    }
    $graphics.DrawLine($axisPen, $left, $top, $left, $top + $plotHeight)
    $graphics.DrawLine($axisPen, $left, $top + $plotHeight, $left + $plotWidth, $top + $plotHeight)

    for ($seriesIndex = 0; $seriesIndex -lt $Series.Count; $seriesIndex++) {
        $series = $Series[$seriesIndex]
        $color = $colors[$seriesIndex % $colors.Count]
        $linePen = [System.Drawing.Pen]::new($color, 2.4)
        $previous = $null
        foreach ($value in $series.values) {
            $timestamp = [double]$value[0]
            $metricValue = [double]$value[1]
            $x = $left + (($timestamp - $From.ToUnixTimeSeconds()) / ($To.ToUnixTimeSeconds() - $From.ToUnixTimeSeconds()) * $plotWidth)
            $y = $top + $plotHeight - ($metricValue / $maxValue * $plotHeight)
            if ($null -ne $previous) {
                $graphics.DrawLine($linePen, $previous.X, $previous.Y, $x, $y)
            }
            $previous = [System.Drawing.PointF]::new($x, $y)
        }

        $legendY = $top + ($seriesIndex * 26)
        $legendX = $left + $plotWidth + 28
        $legendBrush = [System.Drawing.SolidBrush]::new($color)
        $graphics.FillRectangle($legendBrush, $legendX, $legendY + 4, 14, 14)
        $graphics.DrawString((Get-SeriesLabel $series ("series-$seriesIndex")), $legendFont, $textBrush, $legendX + 22, $legendY)
        $legendBrush.Dispose()
        $linePen.Dispose()
    }

    $bitmap.Save((Join-Path $assetsDirectory $FileName), [System.Drawing.Imaging.ImageFormat]::Png)
    $gridPen.Dispose(); $axisPen.Dispose(); $textBrush.Dispose(); $mutedBrush.Dispose()
    $titleFont.Dispose(); $subtitleFont.Dispose(); $axisFont.Dispose(); $legendFont.Dispose()
    $graphics.Dispose(); $bitmap.Dispose()
}

$rps = Get-PrometheusRangeQuery 'sum by (application) (rate(http_server_requests_seconds_count[1m]))'
$p95 = Get-PrometheusRangeQuery 'histogram_quantile(0.95, sum by (le, application) (rate(http_server_requests_seconds_bucket[1m])))'

New-LineChart -FileName 'http-rps.png' -Title 'HTTP RPS' -Unit 'requests / second' -Series $rps
New-LineChart -FileName 'http-p95-latency.png' -Title 'HTTP p95 Latency' -Unit 'seconds' -Series $p95
