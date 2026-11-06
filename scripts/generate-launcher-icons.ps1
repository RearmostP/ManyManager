param(
    [Parameter(Mandatory = $true)][string]$SourceImage
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$res = Join-Path $PSScriptRoot '../androidApp/src/main/res'
$source = [System.Drawing.Image]::FromFile($SourceImage)
try {
    # Trim the reference's broad white border, retaining the rounded artwork and shadow.
    $crop = [System.Drawing.RectangleF]::new(
        [single]($source.Width * 160 / 736),
        [single]($source.Height * 160 / 736),
        [single]($source.Width * 416 / 736),
        [single]($source.Height * 416 / 736))
    $outputs = @{
        'drawable-nodpi/manymanager_launcher_art.png' = 512
        'mipmap-mdpi/ic_launcher.png' = 48
        'mipmap-mdpi/ic_launcher_round.png' = 48
        'mipmap-hdpi/ic_launcher.png' = 72
        'mipmap-hdpi/ic_launcher_round.png' = 72
        'mipmap-xhdpi/ic_launcher.png' = 96
        'mipmap-xhdpi/ic_launcher_round.png' = 96
        'mipmap-xxhdpi/ic_launcher.png' = 144
        'mipmap-xxhdpi/ic_launcher_round.png' = 144
        'mipmap-xxxhdpi/ic_launcher.png' = 192
        'mipmap-xxxhdpi/ic_launcher_round.png' = 192
    }
    foreach ($entry in $outputs.GetEnumerator()) {
        $size = $entry.Value
        $bitmap = [System.Drawing.Bitmap]::new($size, $size)
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        try {
            $graphics.Clear([System.Drawing.Color]::White)
            $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
            $graphics.DrawImage($source, [System.Drawing.RectangleF]::new(0, 0, $size, $size), $crop, [System.Drawing.GraphicsUnit]::Pixel)
            $bitmap.Save((Join-Path $res $entry.Key), [System.Drawing.Imaging.ImageFormat]::Png)
            Write-Output "$($entry.Key): $size x $size"
        } finally {
            $graphics.Dispose()
            $bitmap.Dispose()
        }
    }
} finally {
    $source.Dispose()
}
