[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$SourcePath,
    [Parameter(Mandatory)]
    [string]$OutputPath
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.Drawing

$ResolvedSource = (Resolve-Path $SourcePath).Path
$Source = [Drawing.Image]::FromFile($ResolvedSource)
$Sizes = @(16, 24, 32, 48, 64, 128, 256)
$Images = New-Object System.Collections.Generic.List[byte[]]
try {
    if ($Source.Width -ne $Source.Height) {
        throw 'アイコン元画像は正方形にしてください。'
    }
    foreach ($Size in $Sizes) {
        $Bitmap = New-Object Drawing.Bitmap $Size, $Size, ([Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $Graphics = [Drawing.Graphics]::FromImage($Bitmap)
        try {
            $Graphics.Clear([Drawing.Color]::Transparent)
            $Graphics.CompositingMode = [Drawing.Drawing2D.CompositingMode]::SourceCopy
            $Graphics.CompositingQuality = [Drawing.Drawing2D.CompositingQuality]::HighQuality
            $Graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $Graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
            $Graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::HighQuality
            $Graphics.DrawImage($Source, 0, 0, $Size, $Size)
            $Stream = New-Object IO.MemoryStream
            try {
                $Bitmap.Save($Stream, [Drawing.Imaging.ImageFormat]::Png)
                $Images.Add($Stream.ToArray())
            } finally {
                $Stream.Dispose()
            }
        } finally {
            $Graphics.Dispose()
            $Bitmap.Dispose()
        }
    }
} finally {
    $Source.Dispose()
}

$ResolvedOutput = [IO.Path]::GetFullPath($OutputPath)
New-Item ([IO.Path]::GetDirectoryName($ResolvedOutput)) -ItemType Directory -Force | Out-Null
$File = [IO.File]::Open($ResolvedOutput, [IO.FileMode]::Create, [IO.FileAccess]::Write)
$Writer = New-Object IO.BinaryWriter $File
try {
    $Writer.Write([uint16]0)
    $Writer.Write([uint16]1)
    $Writer.Write([uint16]$Sizes.Count)
    $Offset = 6 + (16 * $Sizes.Count)
    for ($Index = 0; $Index -lt $Sizes.Count; $Index++) {
        $Size = $Sizes[$Index]
        $Dimension = if ($Size -eq 256) { [byte]0 } else { [byte]$Size }
        $Writer.Write($Dimension)
        $Writer.Write($Dimension)
        $Writer.Write([byte]0)
        $Writer.Write([byte]0)
        $Writer.Write([uint16]1)
        $Writer.Write([uint16]32)
        $Writer.Write([uint32]$Images[$Index].Length)
        $Writer.Write([uint32]$Offset)
        $Offset += $Images[$Index].Length
    }
    foreach ($Image in $Images) {
        $Writer.Write($Image)
    }
} finally {
    $Writer.Dispose()
    $File.Dispose()
}
Write-Host "正式アイコンを生成しました: $ResolvedOutput"
