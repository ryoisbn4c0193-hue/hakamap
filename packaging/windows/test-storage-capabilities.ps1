[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$Directory,
    [ValidateSet('local', 'external', 'smb3')]
    [string]$StorageType,
    [string]$ResultDirectory = "$PSScriptRoot/../results"
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ($env:OS -ne 'Windows_NT') {
    throw 'Windows 11実機上で実行してください。'
}
$Root = (Resolve-Path $Directory).Path
$Probe = Join-Path $Root ('.hakamap-storage-probe-' + [Guid]::NewGuid().ToString('N'))
$ResultDirectory = [IO.Path]::GetFullPath($ResultDirectory)
New-Item $ResultDirectory -ItemType Directory -Force | Out-Null
$Checks = [ordered]@{
    createDirectory = $false
    exclusiveLock = $false
    atomicReplaceEquivalent = $false
    writeThroughReadBack = $false
    cleanup = $false
}

try {
    New-Item $Probe -ItemType Directory | Out-Null
    $Checks.createDirectory = $true
    $LockPath = Join-Path $Probe 'stable.lock'
    $First = [IO.File]::Open(
        $LockPath,
        [IO.FileMode]::OpenOrCreate,
        [IO.FileAccess]::ReadWrite,
        [IO.FileShare]::ReadWrite
    )
    try {
        $First.Lock(0, 1)
        $Second = [IO.File]::Open(
            $LockPath,
            [IO.FileMode]::Open,
            [IO.FileAccess]::ReadWrite,
            [IO.FileShare]::ReadWrite
        )
        try {
            try {
                $Second.Lock(0, 1)
                $Second.Unlock(0, 1)
            } catch [IO.IOException] {
                $Checks.exclusiveLock = $true
            }
        } finally {
            $Second.Dispose()
        }
        $First.Unlock(0, 1)
    } finally {
        $First.Dispose()
    }

    $Old = Join-Path $Probe 'project.json'
    $New = Join-Path $Probe 'project.json.new'
    [IO.File]::WriteAllText($Old, '{"value":"old"}')
    [IO.File]::WriteAllText($New, '{"value":"new"}')
    [IO.File]::Replace($New, $Old, $null)
    $Checks.atomicReplaceEquivalent = ([IO.File]::ReadAllText($Old) -eq '{"value":"new"}')

    $Payload = [byte[]](0..255)
    $Data = Join-Path $Probe 'readback.bin'
    $Stream = [IO.File]::Open(
        $Data,
        [IO.FileMode]::CreateNew,
        [IO.FileAccess]::Write,
        [IO.FileShare]::None
    )
    try {
        $Stream.Write($Payload, 0, $Payload.Length)
        $Stream.Flush($true)
    } finally {
        $Stream.Dispose()
    }
    $Checks.writeThroughReadBack =
        [BitConverter]::ToString([IO.File]::ReadAllBytes($Data)) -eq
        [BitConverter]::ToString($Payload)
} catch {
    $Failure = $_.Exception.GetType().Name
} finally {
    Remove-Item $Probe -Recurse -Force -ErrorAction SilentlyContinue
    $Checks.cleanup = -not (Test-Path $Probe)
}

$Passed =
    $Checks.createDirectory -and
    $Checks.exclusiveLock -and
    $Checks.atomicReplaceEquivalent -and
    $Checks.writeThroughReadBack -and
    $Checks.cleanup
$Result = [ordered]@{
    schemaVersion = 1
    recordedAt = [DateTimeOffset]::UtcNow.ToString('O')
    storageType = $StorageType
    passed = $Passed
    checks = $Checks
    failureClass = if (Test-Path variable:Failure) { $Failure } else { $null }
}
$ResultPath = Join-Path $ResultDirectory (
    'storage-' + $StorageType + '-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.json'
)
$Result | ConvertTo-Json -Depth 6 | Set-Content $ResultPath -Encoding utf8
Write-Host "保存先機能検査: $(if ($Passed) { '合格' } else { '不合格' })"
Write-Host "結果: $ResultPath"
if (-not $Passed) {
    exit 2
}
