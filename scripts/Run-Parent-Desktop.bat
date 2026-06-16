@echo off
REM ============================================================
REM Nexus Parent Desktop - Development and Run Script
REM ============================================================
REM This script installs dependencies and runs the Nexus Parent 
REM desktop application using Electron.
REM ============================================================

setlocal enabledelayedexpansion
set "ROOT_DIR=%~dp0.."
set "DESKTOP_PROJECT=%ROOT_DIR%\Nexus Parent\desktop"

echo.
echo ============================================================
echo Nexus Parent Desktop - Run Script
echo ============================================================
echo.

REM Verify project directory exists
if not exist "%DESKTOP_PROJECT%" (
  echo [ERROR] Parent Desktop project not found at:
  echo         "%DESKTOP_PROJECT%"
  echo.
  exit /b 1
)

cd /d "%DESKTOP_PROJECT%"
if errorlevel 1 (
  echo [ERROR] Failed to navigate to Parent Desktop project directory.
  exit /b 1
)

REM Check Node.js installation
echo [CHECK] Verifying Node.js environment...
where node >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Node.js not found in PATH.
  echo         Please install Node.js 20 or later from https://nodejs.org/
  echo.
  exit /b 1
)

node --version >nul 2>&1
for /f "tokens=1,2,3 delims=. " %%a in ('node --version 2^>^&1') do (
  set "NODE_VERSION=%%b"
)
if %NODE_VERSION% LSS 20 (
  echo [WARN] Node.js version is less than 20. Recommended: Node.js 20+
)

REM Check npm installation
echo [CHECK] Verifying npm installation...
where npm >nul 2>&1
if errorlevel 1 (
  echo [ERROR] npm not found in PATH.
  echo         npm should be included with Node.js installation.
  echo.
  exit /b 1
)

echo.
echo [STEP 1/3] Installing dependencies...
echo [ACTION] Running: npm ci
echo.

REM Install dependencies
call npm ci
if errorlevel 1 (
  echo [WARN] npm ci failed. Attempting npm install...
  call npm install
  if errorlevel 1 (
    echo [ERROR] Failed to install dependencies.
    echo         Try manual installation: cd "%DESKTOP_PROJECT%" ^&^& npm install
    exit /b 1
  )
)

echo.
echo [STEP 2/3] Running tests...
echo [ACTION] Running: npm test -- --run
echo.

REM Run tests
call npm test -- --run
if errorlevel 1 (
  echo [WARN] Tests failed. Application will still launch.
  echo        Review test output above for details.
)

echo.
echo [STEP 3/3] Launching Nexus Parent Desktop...
echo [ACTION] Starting Electron application...
echo.

REM Start the application
call npm start
if errorlevel 1 (
  echo [ERROR] Failed to start Nexus Parent Desktop.
  echo         Check console output above for details.
  exit /b 1
)

exit /b 0
