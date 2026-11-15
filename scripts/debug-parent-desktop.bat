@echo off
REM ==============================================================================
REM NEXUS PARENT DESKTOP - VS CODE DEBUG LAUNCHER SCRIPT
REM ==============================================================================
REM This script is invoked by VS Code's "Run and Debug" button (F5) or tasks.
REM It prepares the development environment, ensures dependencies and TypeScript
REM artifacts are built, and executes the Tauri development server with proper
REM exit codes for VS Code process lifecycle management.
REM ==============================================================================

setlocal enabledelayedexpansion

REM Calculate the root workspace directory
set "SCRIPT_DIR=%~dp0"
set "ROOT_DIR=%SCRIPT_DIR%.."
set "DESKTOP_DIR=%ROOT_DIR%\Nexus Parent\desktop"

echo [VSCODE-DEBUG] Preparing Nexus Parent Desktop debug session...

REM Verify desktop project directory exists
if not exist "%DESKTOP_DIR%" (
    echo [VSCODE-DEBUG] [ERROR] Desktop directory not found at: "%DESKTOP_DIR%"
    exit /b 1
)

cd /d "%DESKTOP_DIR%"
if errorlevel 1 (
    echo [VSCODE-DEBUG] [ERROR] Failed to switch directory to: "%DESKTOP_DIR%"
    exit /b 1
)

REM Verify toolchains
where node >nul 2>&1
if errorlevel 1 (
    echo [VSCODE-DEBUG] [ERROR] Node.js is required but was not found in PATH.
    exit /b 1
)

where cargo >nul 2>&1
if errorlevel 1 (
    echo [VSCODE-DEBUG] [ERROR] Cargo Rust toolchain is required but was not found in PATH.
    exit /b 1
)

REM Resolve Tauri command
where cargo-tauri >nul 2>&1
if errorlevel 1 (
    set "TAURI_CMD=npx @tauri-apps/cli"
) else (
    set "TAURI_CMD=cargo tauri"
)

REM Ensure dependencies exist
if not exist "node_modules" (
    echo [VSCODE-DEBUG] [SETUP] Installing npm packages...
    call npm install
    if errorlevel 1 (
        echo [VSCODE-DEBUG] [ERROR] npm install failed.
        exit /b 1
    )
)

REM Ensure TypeScript is compiled
echo [VSCODE-DEBUG] [BUILD] Compiling TypeScript UI...
call npm run build
if errorlevel 1 (
    echo [VSCODE-DEBUG] [ERROR] TypeScript compilation failed.
    exit /b 1
)

REM Launch the Tauri development process with hot-reload
echo [VSCODE-DEBUG] [LAUNCH] Starting Tauri dev server...
call %TAURI_CMD% dev %*
set "EXIT_CODE=%ERRORLEVEL%"

if %EXIT_CODE% neq 0 (
    echo [VSCODE-DEBUG] [ERROR] Process exited with status code: %EXIT_CODE%
    exit /b %EXIT_CODE%
)

echo [VSCODE-DEBUG] Debug session terminated normally.
exit /b 0
