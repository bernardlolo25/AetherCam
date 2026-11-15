#!/usr/bin/env pwsh
<#
.SYNOPSIS
  Generates a multi-resolution Windows .ico file from a source SVG/PNG
  using .NET System.Drawing (no Inkscape or ImageMagick needed).

.USAGE
  pwsh -File Make-Icon.ps1
  Run from the AetherCam repository root or the installer/ directory.
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot  = Split-Path $PSScriptRoot -Parent
$assetsDir = Join-Path $PSScriptRoot 'assets'
$outIco    = Join-Path $assetsDir 'icon.ico'

Add-Type -AssemblyName System.Drawing

# ── Create a 256x256 bitmap with the AetherCam logo drawn via GDI+ ──────────
function New-AetherCamBitmap([int]$size) {
    $bmp = [System.Drawing.Bitmap]::new($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g   = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode   = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic

    $s = $size / 256.0  # scale factor

    # Background rounded rect
    $bgBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 13, 27, 42))
    $g.FillRectangle($bgBrush, 0, 0, $size, $size)
    $bgBrush.Dispose()

    # Camera body (cyan rectangle)
    $camBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 0, 212, 255))
    $g.FillRectangle($camBrush,
        [int](40*$s), [int](72*$s),
        [int](176*$s), [int](128*$s))

    # Viewfinder notch
    $g.FillRectangle($camBrush,
        [int](96*$s), [int](52*$s),
        [int](64*$s), [int](24*$s))
    $camBrush.Dispose()

    # Lens ring (dark)
    $darkBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 13, 27, 42))
    $lensR = [int](52*$s)
    $cx = [int](128*$s); $cy = [int](136*$s)
    $g.FillEllipse($darkBrush, $cx-$lensR, $cy-$lensR, $lensR*2, $lensR*2)
    $darkBrush.Dispose()

    # Lens glass (deep blue)
    $glassBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 0, 29, 46))
    $glassR = [int](44*$s)
    $g.FillEllipse($glassBrush, $cx-$glassR, $cy-$glassR, $glassR*2, $glassR*2)
    $glassBrush.Dispose()

    # Iris (cyan)
    $irisBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 0, 212, 255))
    $irisR = [int](32*$s)
    $g.FillEllipse($irisBrush, $cx-$irisR, $cy-$irisR, $irisR*2, $irisR*2)
    $irisBrush.Dispose()

    # Pupil
    $pupilBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 13, 27, 42))
    $pupilR = [int](20*$s)
    $g.FillEllipse($pupilBrush, $cx-$pupilR, $cy-$pupilR, $pupilR*2, $pupilR*2)
    $pupilBrush.Dispose()

    # Catchlight
    $whiteBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(180, 255, 255, 255))
    $clR = [int](6*$s)
    $g.FillEllipse($whiteBrush, $cx-[int](10*$s)-$clR, $cy-[int](10*$s)-$clR, $clR*2, $clR*2)
    $whiteBrush.Dispose()

    # Flash LED
    $goldBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 255, 215, 0))
    $ledX = [int](192*$s); $ledY = [int](96*$s); $ledR = [int](10*$s)
    $g.FillEllipse($goldBrush, $ledX-$ledR, $ledY-$ledR, $ledR*2, $ledR*2)
    $goldBrush.Dispose()

    $g.Dispose()
    return $bmp
}

# ── Write a multi-resolution .ico file ──────────────────────────────────────
function Write-IcoFile([string]$path, [int[]]$sizes) {
    $bitmaps = $sizes | ForEach-Object { New-AetherCamBitmap $_ }

    # ICO header: ICONDIR
    $ms = [System.IO.MemoryStream]::new()
    $w  = [System.IO.BinaryWriter]::new($ms)

    $count = $sizes.Count
    $w.Write([uint16]0)      # Reserved
    $w.Write([uint16]1)      # Type: ICO
    $w.Write([uint16]$count)

    # Offset of first image data = header (6) + directory entries (count * 16)
    $dataOffset = 6 + $count * 16
    $imageDatas  = @()

    foreach ($bmp in $bitmaps) {
        $imgMs = [System.IO.MemoryStream]::new()
        $bmp.Save($imgMs, [System.Drawing.Imaging.ImageFormat]::Png)
        $imageDatas += ,$imgMs.ToArray()
        $imgMs.Dispose()
        $bmp.Dispose()
    }

    for ($i = 0; $i -lt $count; $i++) {
        $sz   = $sizes[$i]
        $data = $imageDatas[$i]
        $wVal = if ($sz -eq 256) { 0 } else { $sz }
        $w.Write([byte]$wVal)  # Width  (0 = 256)
        $w.Write([byte]$wVal)  # Height (0 = 256)
        $w.Write([byte]0)                          # Color count
        $w.Write([byte]0)                          # Reserved
        $w.Write([uint16]1)                        # Planes
        $w.Write([uint16]32)                       # Bit count
        $w.Write([uint32]$data.Length)             # Size of image data
        $w.Write([uint32]$dataOffset)              # Offset of image data
        $dataOffset += $data.Length
    }

    foreach ($data in $imageDatas) { $w.Write($data) }

    $w.Flush()
    [System.IO.File]::WriteAllBytes($path, $ms.ToArray())
    $w.Dispose()
    $ms.Dispose()
    Write-Host "[+] ICO written: $path ($($sizes -join 'x, ') sizes)" -ForegroundColor Green
}

# ── Main ──────────────────────────────────────────────────────────────────────
Write-Host "Generating AetherCam icon..." -ForegroundColor Cyan
Write-IcoFile -path $outIco -sizes @(16, 24, 32, 48, 64, 128, 256)
Write-Host "Done." -ForegroundColor Cyan
