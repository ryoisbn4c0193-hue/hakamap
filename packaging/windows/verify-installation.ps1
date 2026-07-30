[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$Installer,
    [ValidateSet('minimum', 'recommended')]
    [string]$EnvironmentClass = 'recommended',
    [string]$ResultDirectory = "$PSScriptRoot/../results"
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ($env:OS -ne 'Windows_NT') {
    throw 'Windows 11実機上で実行してください。'
}
$InstallerPath = (Resolve-Path $Installer).Path
$ResultDirectory = [IO.Path]::GetFullPath($ResultDirectory)
New-Item $ResultDirectory -ItemType Directory -Force | Out-Null
$ResultPath = Join-Path $ResultDirectory (
    'windows-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.json'
)

$Checks = [ordered]@{}
$Checks.installerExists = Test-Path $InstallerPath -PathType Leaf
$Checks.installerSha256 = (Get-FileHash $InstallerPath -Algorithm SHA256).Hash.ToLowerInvariant()
$Checks.windows11 = [Environment]::OSVersion.Version.Build -ge 22000
$Checks.nonAdministrator =
    -not ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()
    ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
$Checks.internetDisconnected = $null
$Checks.installSucceeded = $null
$Checks.desktopShortcut = $null
$Checks.startMenuShortcut = $null
$Checks.firstLaunchSeconds = $null
$Checks.secondLaunchReusedProcess = $null
$Checks.reopenAfterBrowserClose = $null
$Checks.inAppShutdown = $null
$Checks.upgradePreservedData = $null
$Checks.uninstallPreservedData = $null

$Result = [ordered]@{
    schemaVersion = 1
    recordedAt = [DateTimeOffset]::UtcNow.ToString('O')
    environmentClass = $EnvironmentClass
    computer = [ordered]@{
        windows = (Get-CimInstance Win32_OperatingSystem).Caption
        build = [Environment]::OSVersion.Version.Build
        cpu = (Get-CimInstance Win32_Processor | Select-Object -First 1).Name
        memoryBytes = (Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory
        resolution = $null
        displayScalePercent = $null
    }
    installer = [ordered]@{
        file = [IO.Path]::GetFileName($InstallerPath)
        sha256 = $Checks.installerSha256
    }
    automatedChecks = $Checks
    manualChecks = [ordered]@{
        status = 'not-run'
        checklist = 'packaging/windows/README.md'
        notes = @()
    }
}
$Result | ConvertTo-Json -Depth 8 | Set-Content $ResultPath -Encoding utf8
Write-Host "検証記録を作成しました: $ResultPath"
Write-Host 'READMEの手動項目を実行し、nullとnot-runを実測結果へ更新してください。'
