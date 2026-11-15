@echo off
REM ==============================================================================
REM NEXUS PARENT DESKTOP - ONE-CLICK RUN SCRIPT
REM ==============================================================================
REM This script is designed to let anyone start the Nexus Parent Desktop application
REM with a single double-click. It performs automatic pre-flight verification,
REM installs any missing Node.js packages, compiles the TypeScript UI, and starts
REM the Tauri v2 desktop application with Rust backend and live hot-reloading.
REM ==============================================================================

REM Keep environment variable changes localized to this script session
setlocal enabledelayedexpansion

REM Determine the repository root directory (one level up from this scripts folder)
set "SCRIPT_DIR=%~dp0"
set "ROOT_DIR=%SCRIPT_DIR%.."
set "DESKTOP_DIR=%ROOT_DIR%\Nexus Parent\desktop"

echo.
echo ==============================================================================
echo  Nexus Parent Desktop - Automated Startup
echo ==============================================================================
echo.

REM ------------------------------------------------------------------------------
REM Step 1: Verify the desktop project directory exists
REM ------------------------------------------------------------------------------
echo [Step 1/5] Checking project directory...
if not exist "%DESKTOP_DIR%" (
    echo [ERROR] Could not find the desktop project directory at:
    echo         "%DESKTOP_DIR%"
    echo.
    echo Please make sure the repository structure is intact.
    goto :ON_ERROR
)
echo [OK] Located project folder: "%DESKTOP_DIR%"

REM Change current working directory to the desktop project directory
cd /d "%DESKTOP_DIR%"
if errorlevel 1 (
    echo [ERROR] Failed to switch working directory to "%DESKTOP_DIR%".
    goto :ON_ERROR
)

REM ------------------------------------------------------------------------------
REM Step 2: Verify required development tools in system PATH
REM ------------------------------------------------------------------------------
echo [Step 2/5] Checking development prerequisites: Node.js, npm, Rust Cargo...

REM Check Node.js
where node >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Node.js is not found in your system PATH.
    echo         Please download and install Node.js 20+ from https://nodejs.org/
    goto :ON_ERROR
)

REM Check npm
where npm >nul 2>&1
if errorlevel 1 (
    echo [ERROR] npm is not found in your system PATH.
    echo         Please ensure npm was installed along with Node.js.
    goto :ON_ERROR
)

REM Check Cargo Rust toolchain
where cargo >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Rust / Cargo toolchain was not found in your system PATH.
    echo         Please install Rust from https://rustup.rs/ and restart your terminal.
    goto :ON_ERROR
)

REM Check Tauri CLI availability
where cargo-tauri >nul 2>&1
if errorlevel 1 (
    echo [INFO] cargo-tauri binary not found directly in PATH.
    echo        Checking if npx @tauri-apps/cli can be used as fallback...
    set "TAURI_CMD=npx @tauri-apps/cli"
) else (
    set "TAURI_CMD=cargo tauri"
)

echo [OK] All core development toolchains are detected.

REM ------------------------------------------------------------------------------
REM Step 3: Check and install frontend npm dependencies if missing
REM ------------------------------------------------------------------------------
echo [Step 3/5] Verifying frontend dependencies...
if not exist "node_modules" (
    echo [INFO] First-time setup detected. node_modules folder is missing.
    echo [INFO] Running 'npm install' to install TypeScript and build tools...
    call npm install
    if errorlevel 1 (
        echo [ERROR] Failed to install npm dependencies.
        goto :ON_ERROR
    )
    echo [OK] Frontend dependencies installed successfully.
) else (
    echo [OK] Frontend dependencies already installed.
)

REM ------------------------------------------------------------------------------
REM Step 4: Compile TypeScript frontend source code
REM ------------------------------------------------------------------------------
echo [Step 4/5] Building TypeScript frontend assets...
call npm run build
if errorlevel 1 (
    echo [ERROR] TypeScript compilation failed.
    echo         Check the compiler error logs above to fix type issues.
    goto :ON_ERROR
)
echo [OK] TypeScript compilation completed cleanly.

REM ------------------------------------------------------------------------------
REM Step 5: Launch Tauri Desktop Application
REM ------------------------------------------------------------------------------
echo [Step 5/5] Launching Nexus Parent Desktop Application...
echo [INFO] Executing command: %TAURI_CMD% dev %*
echo [INFO] Close the application window or press Ctrl+C in this console to stop.
echo.

call %TAURI_CMD% dev %*
if errorlevel 1 (
    echo.
    echo [ERROR] The application exited with an error code.
    goto :ON_ERROR
)

echo.
echo [INFO] Nexus Parent Desktop closed normally.
exit /b 0

REM ------------------------------------------------------------------------------
REM Error Handling Routine: Pauses the console so the user can read the error
REM ------------------------------------------------------------------------------
:ON_ERROR
echo.
echo ==============================================================================
echo  Startup failed! Review the error messages above for troubleshooting.
echo ==============================================================================
echo.
pause
exit /b 1
