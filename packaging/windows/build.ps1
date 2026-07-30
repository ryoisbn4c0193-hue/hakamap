[CmdletBinding()]
param(
    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string]$Version = '0.1.0',
    [string]$IconPath = '',
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$Backend = Join-Path $RepositoryRoot 'backend'
$Output = Join-Path $RepositoryRoot 'packaging/out'
$Work = Join-Path $RepositoryRoot 'packaging/work'
$InputDirectory = Join-Path $Work 'input'
$RuntimeDirectory = Join-Path $Work 'runtime'
$UpgradeUuid = '5e8c8d8f-4985-4e35-9063-320153c36f84'
$Modules = @(
    'java.base',
    'java.desktop',
    'java.instrument',
    'java.logging',
    'java.management',
    'java.naming',
    'java.net.http',
    'java.security.jgss',
    'java.sql',
    'java.xml',
    'jdk.crypto.ec',
    'jdk.unsupported',
    'jdk.zipfs'
) -join ','

if ($env:OS -ne 'Windows_NT') {
    throw 'Windows上で実行してください。jpackageのWindows EXEはクロスビルドしません。'
}
foreach ($Command in @('java', 'jlink', 'jpackage')) {
    if (-not (Get-Command $Command -ErrorAction SilentlyContinue)) {
        throw "Java 21 JDKの$Commandが見つかりません。"
    }
}
if ((java --version | Select-Object -First 1) -notmatch '21[.]') {
    throw 'Java 21 JDKを使用してください。'
}
if ([string]::IsNullOrWhiteSpace($IconPath)) {
    $IconPath = Join-Path $Work 'hakamap-placeholder.ico'
    & (Join-Path $PSScriptRoot 'create-placeholder-icon.ps1') -OutputPath $IconPath
    if (-not $?) {
        throw '仮アイコン生成に失敗しました。'
    }
}
$ResolvedIcon = (Resolve-Path $IconPath).Path
if ([IO.Path]::GetExtension($ResolvedIcon) -ne '.ico') {
    throw 'アイコンは.ico形式で指定してください。'
}

Remove-Item $Work -Recurse -Force -ErrorAction SilentlyContinue
New-Item $InputDirectory -ItemType Directory -Force | Out-Null
New-Item $Output -ItemType Directory -Force | Out-Null

Push-Location $Backend
try {
    if ($SkipTests) {
        & .\mvnw.cmd -DskipTests package
    } else {
        & .\mvnw.cmd verify
    }
    if ($LASTEXITCODE -ne 0) {
        throw 'Mavenビルドに失敗しました。'
    }
} finally {
    Pop-Location
}

$Jar = Get-ChildItem (Join-Path $Backend 'target/hakamap-*.jar') |
    Where-Object { $_.Name -notmatch '\.original$' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if ($null -eq $Jar) {
    throw 'パッケージ対象JARが見つかりません。'
}
Copy-Item $Jar.FullName (Join-Path $InputDirectory 'hakamap.jar')

& jlink `
    --add-modules $Modules `
    --strip-debug `
    --no-header-files `
    --no-man-pages `
    --compress zip-6 `
    --output $RuntimeDirectory
if ($LASTEXITCODE -ne 0) {
    throw 'jlinkランタイム作成に失敗しました。'
}

& jpackage `
    --type exe `
    --name Hakamap `
    --description '墓地マップ管理アプリ' `
    --vendor Hakamap `
    --app-version $Version `
    --input $InputDirectory `
    --main-jar hakamap.jar `
    --runtime-image $RuntimeDirectory `
    --dest $Output `
    --icon $ResolvedIcon `
    --win-per-user-install `
    --win-menu `
    --win-menu-group Hakamap `
    --win-shortcut `
    --win-upgrade-uuid $UpgradeUuid `
    --java-options '-Dfile.encoding=UTF-8' `
    --java-options '-Xmx512m'
if ($LASTEXITCODE -ne 0) {
    throw 'jpackage EXE作成に失敗しました。'
}

$Generated = Get-ChildItem $Output -Filter 'Hakamap-*.exe' |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if ($null -eq $Generated) {
    throw 'EXEインストーラーが生成されませんでした。'
}
$Expected = Join-Path $Output "Hakamap-$Version.exe"
if ($Generated.FullName -ne $Expected) {
    Move-Item $Generated.FullName $Expected -Force
}
$Hash = (Get-FileHash $Expected -Algorithm SHA256).Hash.ToLowerInvariant()
@{
    version = $Version
    file = [IO.Path]::GetFileName($Expected)
    sha256 = $Hash
    upgradeUuid = $UpgradeUuid
    createdAt = [DateTimeOffset]::UtcNow.ToString('O')
} | ConvertTo-Json | Set-Content (Join-Path $Output 'manifest.json') -Encoding utf8

Write-Host "生成完了: $Expected"
Write-Host "SHA-256: $Hash"
