@echo off
echo ========================================
echo   Nexus Parent - Electron App
echo ========================================
echo.

REM Check if node_modules exists
if not exist "node_modules\" (
    echo [INFO] node_modules not found. Installing dependencies...
    echo.
    call npm install
    if errorlevel 1 (
        echo.
        echo [ERROR] Failed to install dependencies!
        echo Please make sure Node.js and npm are installed.
        pause
        exit /b 1
    )
    echo.
    echo [SUCCESS] Dependencies installed successfully!
    echo.
)

echo [INFO] Starting Electron app in development mode...
echo.
call npm run dev

if errorlevel 1 (
    echo.
    echo [ERROR] Failed to start the app!
    pause
    exit /b 1
)
