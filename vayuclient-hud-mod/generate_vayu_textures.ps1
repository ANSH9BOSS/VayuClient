Add-Type -AssemblyName PresentationCore, WindowsBase, PresentationFramework, System.Drawing

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$resGui = Join-Path $scriptDir "src\main\resources\assets\vayuclient-hud\textures\gui"
$resTitle = Join-Path $resGui "title"
$vayuLogoMaster = "C:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\VayuClient\Assets\Images\vayu_logo.png"

if (-not (Test-Path $resGui)) { New-Item -ItemType Directory -Path $resGui -Force }
if (-not (Test-Path $resTitle)) { New-Item -ItemType Directory -Path $resTitle -Force }

function Save-DrawingVisualToPng($visual, [int]$width, [int]$height, [string]$outputPath) {
    $rtb = New-Object System.Windows.Media.Imaging.RenderTargetBitmap($width, $height, 96, 96, [System.Windows.Media.PixelFormats]::Pbgra32)
    $rtb.Render($visual)
    $encoder = New-Object System.Windows.Media.Imaging.PngBitmapEncoder
    $frame = [System.Windows.Media.Imaging.BitmapFrame]::Create($rtb)
    $encoder.Frames.Add($frame)
    $fs = [System.IO.File]::Open($outputPath, [System.IO.FileMode]::Create)
    $encoder.Save($fs)
    $fs.Close()
    Write-Host "-> Generated: $outputPath ($width x $height)" -ForegroundColor Green
}

# 1. Generate title-vayuclient-logo.png (510 x 161)
$logoVis = New-Object System.Windows.Media.DrawingVisual
$dc = $logoVis.RenderOpen()

# Load master vayu logo
$logoUri = New-Object System.Uri($vayuLogoMaster, [System.UriKind]::Absolute)
$logoBmp = New-Object System.Windows.Media.Imaging.BitmapImage
$logoBmp.BeginInit()
$logoBmp.UriSource = $logoUri
$logoBmp.CacheOption = [System.Windows.Media.Imaging.BitmapCacheOption]::OnLoad
$logoBmp.EndInit()

# Draw Vayu Icon on the left
$iconSize = 135
$iconX = 15
$iconY = (161 - $iconSize) / 2
$dc.DrawImage($logoBmp, [System.Windows.Rect]::new($iconX, $iconY, $iconSize, $iconSize))

# Draw "VAYUCLIENT" text on the right
$typeface = New-Object System.Windows.Media.Typeface([System.Windows.Media.FontFamily]::new("Segoe UI, Montserrat, Arial"), [System.Windows.FontStyles]::Normal, [System.Windows.FontWeights]::Black, [System.Windows.FontStretches]::Normal)

# Main Cyan "VAYUCLIENT" Text
$cyanBrush = New-Object System.Windows.Media.LinearGradientBrush(
    [System.Windows.Media.Color]::FromArgb(255, 0, 210, 255),
    [System.Windows.Media.Color]::FromArgb(255, 56, 189, 248),
    [System.Windows.Point]::new(0, 0),
    [System.Windows.Point]::new(1, 1)
)

$formattedText = New-Object System.Windows.Media.FormattedText(
    "VAYUCLIENT",
    [System.Globalization.CultureInfo]::InvariantCulture,
    [System.Windows.FlowDirection]::LeftToRight,
    $typeface,
    48,
    $cyanBrush,
    96
)

# Text Glow / Shadow
$shadowBrush = New-Object System.Windows.Media.SolidColorBrush([System.Windows.Media.Color]::FromArgb(180, 0, 100, 180))
$shadowText = New-Object System.Windows.Media.FormattedText(
    "VAYUCLIENT",
    [System.Globalization.CultureInfo]::InvariantCulture,
    [System.Windows.FlowDirection]::LeftToRight,
    $typeface,
    48,
    $shadowBrush,
    96
)

$textX = $iconX + $iconSize + 15
$textY = 56
$dc.DrawText($shadowText, [System.Windows.Point]::new($textX + 2, $textY + 2))
$dc.DrawText($formattedText, [System.Windows.Point]::new($textX, $textY))

$dc.Close()
Save-DrawingVisualToPng $logoVis 510 161 (Join-Path $resGui "title-vayuclient-logo.png")
Save-DrawingVisualToPng $logoVis 510 161 (Join-Path $resGui "title-vayuclient-logo_1x.png")

# 2. Copy logo.png
Copy-Item $vayuLogoMaster (Join-Path $resGui "logo.png") -Force
Copy-Item $vayuLogoMaster (Join-Path $resGui "logo_1x.png") -Force

# 3. Generate fasticon_white.png (96 x 96) (Clean Vayu Client Logo)
$iconVis = New-Object System.Windows.Media.DrawingVisual
$idc = $iconVis.RenderOpen()
$idc.DrawImage($logoBmp, [System.Windows.Rect]::new(4, 4, 88, 88))
$idc.Close()
Save-DrawingVisualToPng $iconVis 96 96 (Join-Path $resGui "fasticon_white.png")
Save-DrawingVisualToPng $iconVis 96 96 (Join-Path $resGui "fasticon_white_1x.png")

# 4. Generate new-background.png (1920 x 1080)
$heroSource = "C:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\VayuClient\Assets\Images\bg_mountain_aurora.jpg"
if (-not (Test-Path $heroSource)) {
    $heroSource = "C:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\VayuClient\Assets\Images\vayu_minecraft_hero.jpg"
}

$bgVis = New-Object System.Windows.Media.DrawingVisual
$bgdc = $bgVis.RenderOpen()

# Fill with deep obsidian space base
$baseBrush = New-Object System.Windows.Media.SolidColorBrush([System.Windows.Media.Color]::FromArgb(255, 6, 9, 15))
$bgdc.DrawRectangle($baseBrush, $null, [System.Windows.Rect]::new(0, 0, 1920, 1080))

if (Test-Path $heroSource) {
    $bgUri = New-Object System.Uri($heroSource, [System.UriKind]::Absolute)
    $bgBmp = New-Object System.Windows.Media.Imaging.BitmapImage
    $bgBmp.BeginInit()
    $bgBmp.UriSource = $bgUri
    $bgBmp.CacheOption = [System.Windows.Media.Imaging.BitmapCacheOption]::OnLoad
    $bgBmp.EndInit()
    
    # Draw background image with dark obsidian overlay
    $bgdc.DrawImage($bgBmp, [System.Windows.Rect]::new(0, 0, 1920, 1080))
    
    $darkOverlay = New-Object System.Windows.Media.SolidColorBrush([System.Windows.Media.Color]::FromArgb(180, 5, 8, 14))
    $bgdc.DrawRectangle($darkOverlay, $null, [System.Windows.Rect]::new(0, 0, 1920, 1080))
}

# Subtle Cyan radial center glow
$radialGlow = New-Object System.Windows.Media.RadialGradientBrush
$radialGlow.GradientOrigin = [System.Windows.Point]::new(0.5, 0.45)
$radialGlow.Center = [System.Windows.Point]::new(0.5, 0.45)
$radialGlow.RadiusX = 0.55
$radialGlow.RadiusY = 0.55
$radialGlow.GradientStops.Add((New-Object System.Windows.Media.GradientStop([System.Windows.Media.Color]::FromArgb(45, 0, 210, 255), 0.0)))
$radialGlow.GradientStops.Add((New-Object System.Windows.Media.GradientStop([System.Windows.Media.Color]::FromArgb(0, 0, 0, 0), 1.0)))
$bgdc.DrawRectangle($radialGlow, $null, [System.Windows.Rect]::new(0, 0, 1920, 1080))

# Dark Vignette around edges
$vignette = New-Object System.Windows.Media.RadialGradientBrush
$vignette.GradientOrigin = [System.Windows.Point]::new(0.5, 0.5)
$vignette.Center = [System.Windows.Point]::new(0.5, 0.5)
$vignette.RadiusX = 0.8
$vignette.RadiusY = 0.8
$vignette.GradientStops.Add((New-Object System.Windows.Media.GradientStop([System.Windows.Media.Color]::FromArgb(0, 0, 0, 0), 0.4)))
$vignette.GradientStops.Add((New-Object System.Windows.Media.GradientStop([System.Windows.Media.Color]::FromArgb(210, 3, 5, 9), 1.0)))
$bgdc.DrawRectangle($vignette, $null, [System.Windows.Rect]::new(0, 0, 1920, 1080))

$bgdc.Close()
Save-DrawingVisualToPng $bgVis 1920 1080 (Join-Path $resGui "new-background.png")
Save-DrawingVisualToPng $bgVis 1920 1080 (Join-Path $resGui "new-background_1x.png")

# 5. Generate Vayu Feature Panel (joindiscord1.png and joindiscord1_hover.png)
function Generate-VayuFeaturePanel($isHover, $outPath) {
    $dVis = New-Object System.Windows.Media.DrawingVisual
    $ddc = $dVis.RenderOpen()
    
    $cardW = 320
    $cardH = 180
    
    # Obsidian glass panel background
    $panelBrush = New-Object System.Windows.Media.SolidColorBrush([System.Windows.Media.Color]::FromArgb(235, 10, 17, 26))
    $borderColor = if ($isHover) { [System.Windows.Media.Color]::FromArgb(255, 0, 217, 255) } else { [System.Windows.Media.Color]::FromArgb(60, 56, 189, 248) }
    $borderBrush = New-Object System.Windows.Media.SolidColorBrush($borderColor)
    $penThickness = if ($isHover) { 1.8 } else { 1.0 }
    $pen = New-Object System.Windows.Media.Pen($borderBrush, $penThickness)
    
    $ddc.DrawRoundedRectangle($panelBrush, $pen, [System.Windows.Rect]::new(2, 2, $cardW - 4, $cardH - 4), 10, 10)
    
    # Cyber Cyan ambient gradient at the top
    $glowAlpha = if ($isHover) { 45 } else { 20 }
    $topGlow = New-Object System.Windows.Media.LinearGradientBrush(
        [System.Windows.Media.Color]::FromArgb($glowAlpha, 0, 217, 255),
        [System.Windows.Media.Color]::FromArgb(0, 2, 132, 199),
        [System.Windows.Point]::new(0, 0),
        [System.Windows.Point]::new(1, 1)
    )
    $ddc.DrawRoundedRectangle($topGlow, $null, [System.Windows.Rect]::new(2, 2, $cardW - 4, $cardH - 4), 10, 10)
    
    # Header Tag: "✦ VAYU ENGINE"
    $tagTypeface = New-Object System.Windows.Media.Typeface([System.Windows.Media.FontFamily]::new("Segoe UI, Montserrat, Arial"), [System.Windows.FontStyles]::Normal, [System.Windows.FontWeights]::Bold, [System.Windows.FontStretches]::Normal)
    $tagBrush = New-Object System.Windows.Media.SolidColorBrush([System.Windows.Media.Color]::FromArgb(255, 56, 189, 248))
    $tagText = New-Object System.Windows.Media.FormattedText(
        "✦ VAYU PERFORMANCE ENGINE",
        [System.Globalization.CultureInfo]::InvariantCulture,
        [System.Windows.FlowDirection]::LeftToRight,
        $tagTypeface,
        12,
        $tagBrush,
        96
    )
    $ddc.DrawText($tagText, [System.Windows.Point]::new(18, 16))
    
    # Title: "Ultra-Low Latency"
    $tTypeface = New-Object System.Windows.Media.Typeface([System.Windows.Media.FontFamily]::new("Segoe UI, Montserrat, Arial"), [System.Windows.FontStyles]::Normal, [System.Windows.FontWeights]::Black, [System.Windows.FontStretches]::Normal)
    $tTextBrush = New-Object System.Windows.Media.SolidColorBrush([System.Windows.Media.Color]::FromArgb(255, 241, 245, 249))
    $tText = New-Object System.Windows.Media.FormattedText(
        "High-FPS Render Pipeline",
        [System.Globalization.CultureInfo]::InvariantCulture,
        [System.Windows.FlowDirection]::LeftToRight,
        $tTypeface,
        17,
        $tTextBrush,
        96
    )
    $ddc.DrawText($tText, [System.Windows.Point]::new(18, 36))
    
    # Description
    $descTypeface = New-Object System.Windows.Media.Typeface([System.Windows.Media.FontFamily]::new("Segoe UI, Arial"), [System.Windows.FontStyles]::Normal, [System.Windows.FontWeights]::Normal, [System.Windows.FontStretches]::Normal)
    $descBrush = New-Object System.Windows.Media.SolidColorBrush([System.Windows.Media.Color]::FromArgb(220, 148, 163, 184))
    $descText = New-Object System.Windows.Media.FormattedText(
        "Engineered with zero-allocation render passes, optimized tick rate, and GPU buffer management.",
        [System.Globalization.CultureInfo]::InvariantCulture,
        [System.Windows.FlowDirection]::LeftToRight,
        $descTypeface,
        11.5,
        $descBrush,
        96
    )
    $descText.MaxTextWidth = $cardW - 36
    $ddc.DrawText($descText, [System.Windows.Point]::new(18, 64))
    
    # Bottom Status Pill
    $pillColor = if ($isHover) { [System.Windows.Media.Color]::FromArgb(40, 0, 217, 255) } else { [System.Windows.Media.Color]::FromArgb(25, 56, 189, 248) }
    $pillBrush = New-Object System.Windows.Media.SolidColorBrush($pillColor)
    $pillBorder = if ($isHover) { [System.Windows.Media.Pen]::new((New-Object System.Windows.Media.SolidColorBrush([System.Windows.Media.Color]::FromArgb(180, 0, 217, 255))), 1.0) } else { [System.Windows.Media.Pen]::new((New-Object System.Windows.Media.SolidColorBrush([System.Windows.Media.Color]::FromArgb(80, 56, 189, 248))), 1.0) }
    $ddc.DrawRoundedRectangle($pillBrush, $pillBorder, [System.Windows.Rect]::new(18, 126, $cardW - 36, 34), 6, 6)
    
    $btnText = New-Object System.Windows.Media.FormattedText(
        "● ACTIVE: 26.2 FABRIC OPTIMIZED",
        [System.Globalization.CultureInfo]::InvariantCulture,
        [System.Windows.FlowDirection]::LeftToRight,
        [System.Windows.Media.Typeface]::new("Segoe UI, Arial", [System.Windows.FontStyles]::Normal, [System.Windows.FontWeights]::Bold, [System.Windows.FontStretches]::Normal),
        11,
        [System.Windows.Media.SolidColorBrush]::new([System.Windows.Media.Color]::FromArgb(255, 34, 197, 94)),
        96
    )
    $ddc.DrawText($btnText, [System.Windows.Point]::new(32, 135))
    
    $ddc.Close()
    Save-DrawingVisualToPng $dVis $cardW $cardH $outPath
}

Generate-VayuFeaturePanel $false (Join-Path $resTitle "joindiscord1.png")
Generate-VayuFeaturePanel $false (Join-Path $resTitle "joindiscord1_1x.png")
Generate-VayuFeaturePanel $true (Join-Path $resTitle "joindiscord1_hover.png")
Generate-VayuFeaturePanel $true (Join-Path $resTitle "joindiscord1_hover_1x.png")

Write-Host "`nAll VayuClient GUI texture assets generated successfully!" -ForegroundColor Cyan
