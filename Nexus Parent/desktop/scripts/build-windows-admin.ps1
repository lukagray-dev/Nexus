#Requires -RunAsAdministrator

# Change to project root
Set-Location (Split-Path -Parent $PSScriptRoot)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Nexus Parent - Windows Installer Build" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Working directory: $(Get-Location)" -ForegroundColor Gray
Write-Host ""

try {
    $nodeVersion = node --version
    Write-Host "[✓] Node.js: $nodeVersion" -ForegroundColor Green
}
catch {
    Write-Host "[✗] Node.js not found" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host ""
Write-Host "[1/4] Installing dependencies..." -ForegroundColor Yellow

if (-not (Test-Path "node_modules")) {
    npm install
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[✗] npm install failed" -ForegroundColor Red
        Read-Host "Press Enter to exit"
        exit 1
    }
}
Write-Host "[✓] Dependencies ready" -ForegroundColor Green

Write-Host ""
Write-Host "[2/4] Generating icons..." -ForegroundColor Yellow

node .\scripts\build-icons-simple.js
if ($LASTEXITCODE -eq 0) {
    Write-Host "[✓] Icons generated" -ForegroundColor Green
}
else {
    Write-Host "[!] Icon generation failed, continuing..." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "[3/4] Setting up build cache..." -ForegroundColor Yellow

& .\scripts\setup-cache-admin.ps1

Write-Host ""
Write-Host "[4/4] Building installer..." -ForegroundColor Yellow
Write-Host "This may take several minutes..." -ForegroundColor Cyan
Write-Host ""

npm run build:win

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "[✗] Build failed" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "SUCCESS: Build Complete" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Get-ChildItem -Path "release" -Filter "*.exe" | ForEach-Object {
    $size = [math]::Round($_.Length / 1MB, 2)
    Write-Host "  $($_.Name) ($size MB)" -ForegroundColor White
}

Write-Host ""
Write-Host "Output: release\Nexus Parent-Setup-1.0.0.exe" -ForegroundColor Green
Write-Host ""
Read-Host "Press Enter to exit"
