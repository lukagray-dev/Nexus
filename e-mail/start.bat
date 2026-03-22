@echo off
setlocal enabledelayedexpansion

color 0E
cls

echo.
echo    NEXUS EMAIL SERVICE - LOCAL DEPLOYMENT
echo.
echo    =======================================
echo.

echo [*] Checking Node.js installation...
where node >nul 2>nul
if %errorlevel% neq 0 (
    echo [X] Node.js is not installed!
    echo     Please install Node.js 18+ from https://nodejs.org/
    echo.
    pause
    exit /b 1
)

for /f "tokens=*" %%i in ('node --version') do set NODE_VERSION=%%i
echo [OK] Node.js found: %NODE_VERSION%
echo.

echo [*] Checking environment configuration...
if not exist ".env" (
    echo [X] .env file not found!
    echo     Please create .env file from env.example
    echo     and fill in your Firebase and Brevo credentials
    echo.
    pause
    exit /b 1
)
echo [OK] Environment file found
echo.

echo [*] Checking dependencies...
if not exist "node_modules" (
    echo [!] Dependencies not installed. Installing now...
    echo.
    call npm install
    if %errorlevel% neq 0 (
        echo.
        echo [X] Failed to install dependencies!
        echo.
        pause
        exit /b 1
    )
    echo.
    echo [OK] Dependencies installed successfully
) else (
    echo [OK] Dependencies already installed
)
echo.

echo    =======================================
echo    NEXUS EMAIL SERVICE
echo    =======================================
echo.
echo    Service URL: http://localhost:3000
echo    Health Check: GET /
echo    Send OTP: POST /send-otp
echo    Password Reset: POST /send-password-reset
echo.
echo    =======================================
echo.

echo [*] Starting Nexus Email Service...
echo     Press Ctrl+C to stop the service
echo.
echo    =======================================
echo.

node server.js

echo.
echo [!] Service stopped
echo.
pause
