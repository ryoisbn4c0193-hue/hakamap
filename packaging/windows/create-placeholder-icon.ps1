[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$OutputPath
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.Drawing

$Sizes = @(16, 24, 32, 48, 64, 128, 256)
$Images = New-Object System.Collections.Generic.List[byte[]]
foreach ($Size in $Sizes) {
    $Bitmap = New-Object Drawing.Bitmap $Size, $Size
    $Graphics = [Drawing.Graphics]::FromImage($Bitmap)
    try {
        $Graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $Graphics.Clear([Drawing.Color]::Transparent)
        $Background = New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb(255, 31, 91, 98))
        $Grave = New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb(255, 242, 238, 222))
        $Path = New-Object Drawing.Drawing2D.GraphicsPath
        try {
            $Margin = [Math]::Max(1, [Math]::Round($Size * 0.06))
            $Diameter = $Size - (2 * $Margin)
            $Path.AddEllipse($Margin, $Margin, $Diameter, $Diameter)
            $Graphics.FillPath($Background, $Path)
            $Unit = $Size / 16.0
            foreach ($Cell in @(
                @(4.0, 4.2, 3.2, 2.5),
                @(8.3, 4.2, 3.2, 2.5),
                @(4.0, 8.2, 3.2, 2.5),
                @(8.3, 8.2, 3.2, 2.5)
            )) {
                $Graphics.FillRectangle(
                    $Grave,
                    [single]($Cell[0] * $Unit),
                    [single]($Cell[1] * $Unit),
                    [single]($Cell[2] * $Unit),
                    [single]($Cell[3] * $Unit)
                )
            }
        } finally {
            $Path.Dispose()
            $Background.Dispose()
            $Grave.Dispose()
        }
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
Write-Host "仮アイコンを生成しました: $ResolvedOutput"
