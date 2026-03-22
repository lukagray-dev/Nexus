@echo off
REM Windows Installer Build Script
REM Run as Administrator (Right-click -> Run as administrator)

net session >nul 2>&1
if %errorLevel% neq 0 (
    echo ERROR: Administrator privileges required
    echo Right-click this file and select "Run as administrator"
    pause
    exit /b 1
)

REM Change to script directory
cd /d "%~dp0.."

echo ========================================
echo Nexus Parent - Windows Installer Build
echo ========================================
echo.
echo Working directory: %CD%
echo.

where node >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Node.js not found
    pause
    exit /b 1
)

echo [1/4] Installing dependencies...
if not exist "node_modules\" (
    call npm install
    if %ERRORLEVEL% NEQ 0 (
        echo ERROR: npm install failed
        pause
        exit /b 1
    )
)

echo [2/4] Generating icons...
node scripts\build-icons-simple.js

echo [3/4] Setting up build cache...
powershell -ExecutionPolicy Bypass -File "%~dp0setup-cache-admin.ps1"

echo [4/4] Building installer...
call npm run build:win

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Build failed
    pause
    exit /b 1
)

echo.
echo ========================================
echo SUCCESS: Build Complete
echo ========================================
echo.
dir /b release\*.exe 2>nul
echo.
echo Output: release\Nexus Parent-Setup-1.0.0.exe
echo.
pause

