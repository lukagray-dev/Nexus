#Requires -RunAsAdministrator

# Change to project root
Set-Location (Split-Path -Parent $PSScriptRoot)

$cacheDir = "$env:LOCALAPPDATA\electron-builder\Cache\winCodeSign"
$url = "https://github.com/electron-userland/electron-builder-binaries/releases/download/winCodeSign-2.6.0/winCodeSign-2.6.0.7z"

if (Test-Path $cacheDir) {
    Remove-Item -Recurse -Force $cacheDir
}
New-Item -ItemType Directory -Force -Path $cacheDir | Out-Null

$zipPath = "$cacheDir\winCodeSign-2.6.0.7z"
Invoke-WebRequest -Uri $url -OutFile $zipPath -UseBasicParsing

$7zPath = ".\node_modules\7zip-bin\win\x64\7za.exe"
$extractPath = "$cacheDir\winCodeSign-2.6.0"

& $7zPath x -bd $zipPath "-o$extractPath" -y | Out-Null

if (Test-Path "$extractPath\win") {
    Write-Host "[✓] Build cache ready" -ForegroundColor Green
}

