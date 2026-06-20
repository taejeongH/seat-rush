param(
    [Parameter(Mandatory = $true)]
    [string]$SummaryPath,

    [Parameter(Mandatory = $true)]
    [string]$OutputPath,

    [Parameter(Mandatory = $true)]
    [string]$Title
)

Add-Type -AssemblyName System.Drawing

$summary = Get-Content -Raw -Encoding utf8 $SummaryPath | ConvertFrom-Json
$metrics = $summary.metrics
$items = @(
    @{ Label = 'Queue join'; Key = 'http_req_duration{name:practice.queue.join}'; Target = 1000 },
    @{ Label = 'Queue enter'; Key = 'http_req_duration{name:practice.queue.enter}'; Target = 1000 },
    @{ Label = 'Seat list'; Key = 'http_req_duration{name:practice.seats}'; Target = 1500 },
    @{ Label = 'Seat hold'; Key = 'http_req_duration{name:practice.seats.hold}'; Target = 1500 },
    @{ Label = 'Reservation'; Key = 'http_req_duration{name:practice.reservation.create}'; Target = 1500 },
    @{ Label = 'Payment'; Key = 'http_req_duration{name:practice.payment.complete}'; Target = 1500 }
) | ForEach-Object {
    [pscustomobject]@{
        Label = $_.Label
        Target = $_.Target
        Value = [double]$metrics.($_.Key).'p(95)'
    }
}

$width = 920
$height = 430
$left = 150
$right = 105
$top = 84
$bottom = 60
$plotWidth = $width - $left - $right
$plotHeight = $height - $top - $bottom
$maxValue = [math]::Max(2000, (($items.Value | Measure-Object -Maximum).Maximum * 1.15))

$bitmap = [System.Drawing.Bitmap]::new($width, $height)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.Clear([System.Drawing.Color]::White)

$titleFont = [System.Drawing.Font]::new('Arial', 22, [System.Drawing.FontStyle]::Bold)
$subtitleFont = [System.Drawing.Font]::new('Arial', 11)
$labelFont = [System.Drawing.Font]::new('Arial', 11)
$valueFont = [System.Drawing.Font]::new('Arial', 10, [System.Drawing.FontStyle]::Bold)
$textBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(25, 35, 45))
$mutedBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(90, 105, 120))
$gridPen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(225, 230, 235), 1)
$targetPen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(100, 116, 139), 1)
$targetPen.DashStyle = [System.Drawing.Drawing2D.DashStyle]::Dash
$successBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(22, 163, 74))
$warningBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(220, 38, 38))

$graphics.DrawString($Title, $titleFont, $textBrush, 28, 22)
$graphics.DrawString('API p95 latency (milliseconds)', $subtitleFont, $mutedBrush, 30, 55)

for ($index = 0; $index -le 4; $index++) {
    $ratio = $index / 4.0
    $x = $left + ($ratio * $plotWidth)
    $value = $maxValue * $ratio
    $graphics.DrawLine($gridPen, $x, $top, $x, $top + $plotHeight)
    $graphics.DrawString(('{0:N0}' -f $value), $labelFont, $mutedBrush, $x - 14, $top + $plotHeight + 12)
}

$barHeight = 26
$gap = 18
for ($index = 0; $index -lt $items.Count; $index++) {
    $item = $items[$index]
    $y = $top + ($index * ($barHeight + $gap))
    $barWidth = $item.Value / $maxValue * $plotWidth
    $targetX = $left + ($item.Target / $maxValue * $plotWidth)
    $brush = if ($item.Value -le $item.Target) { $successBrush } else { $warningBrush }

    $graphics.DrawString($item.Label, $labelFont, $textBrush, 28, $y + 4)
    $graphics.FillRectangle($brush, $left, $y, $barWidth, $barHeight)
    $graphics.DrawLine($targetPen, $targetX, $y - 4, $targetX, $y + $barHeight + 4)
    $graphics.DrawString(('{0:N0} ms' -f $item.Value), $valueFont, $textBrush, $left + $barWidth + 7, $y + 4)
}

$graphics.DrawLine($targetPen, 650, 45, 670, 45)
$graphics.DrawString('p95 target', $labelFont, $mutedBrush, 677, 37)

$outputDirectory = Split-Path -Parent $OutputPath
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
$bitmap.Save($OutputPath, [System.Drawing.Imaging.ImageFormat]::Png)

$gridPen.Dispose(); $targetPen.Dispose(); $successBrush.Dispose(); $warningBrush.Dispose()
$textBrush.Dispose(); $mutedBrush.Dispose(); $titleFont.Dispose(); $subtitleFont.Dispose(); $labelFont.Dispose(); $valueFont.Dispose()
$graphics.Dispose(); $bitmap.Dispose()
