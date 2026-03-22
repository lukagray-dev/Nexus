@echo off
echo ========================================
echo Testing Nexus Email Service API
echo ========================================
echo.

set API_URL=https://e-mail-service.vercel.app

echo [1/3] Testing Health Check...
curl -s %API_URL%/
echo.
echo.

echo [2/3] Testing OTP Send (enter your email):
set /p EMAIL="Enter email address: "
echo Sending OTP to %EMAIL%...
curl -X POST %API_URL%/send-otp -H "Content-Type: application/json" -d "{\"email\":\"%EMAIL%\"}"
echo.
echo.

echo [3/3] Testing OTP Verify (enter the OTP you received):
set /p OTP="Enter OTP code: "
echo Verifying OTP...
curl -X POST %API_URL%/verify-otp -H "Content-Type: application/json" -d "{\"email\":\"%EMAIL%\",\"otp\":\"%OTP%\"}"
echo.
echo.

echo ========================================
echo Test Complete!
echo ========================================
pause
