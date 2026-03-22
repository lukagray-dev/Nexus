# PowerShell Test Script for Nexus Email Service
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Testing Nexus Email Service API" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$apiUrl = "https://e-mail-service.vercel.app"

# Test 1: Health Check
Write-Host "[1/3] Testing Health Check..." -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$apiUrl/" -Method Get
    Write-Host "✓ Health check passed!" -ForegroundColor Green
    Write-Host "Status: $($response.status)" -ForegroundColor Gray
    Write-Host "Message: $($response.message)" -ForegroundColor Gray
} catch {
    Write-Host "✗ Health check failed!" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
}
Write-Host ""

# Test 2: Send OTP
Write-Host "[2/3] Testing OTP Send..." -ForegroundColor Yellow
$email = Read-Host "Enter your email address"
Write-Host "Sending OTP to $email..." -ForegroundColor Gray

try {
    $body = @{
        email = $email
    } | ConvertTo-Json

    $response = Invoke-RestMethod -Uri "$apiUrl/send-otp" -Method Post -Body $body -ContentType "application/json"
    Write-Host "✓ OTP sent successfully!" -ForegroundColor Green
    Write-Host "Message: $($response.message)" -ForegroundColor Gray
    Write-Host "Message ID: $($response.messageId)" -ForegroundColor Gray
    Write-Host ""
    Write-Host "Check your email for the OTP code!" -ForegroundColor Cyan
} catch {
    Write-Host "✗ OTP send failed!" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    if ($_.ErrorDetails.Message) {
        Write-Host $_.ErrorDetails.Message -ForegroundColor Red
    }
}
Write-Host ""

# Test 3: Verify OTP
Write-Host "[3/3] Testing OTP Verify..." -ForegroundColor Yellow
$otp = Read-Host "Enter the OTP code you received"
Write-Host "Verifying OTP..." -ForegroundColor Gray

try {
    $body = @{
        email = $email
        otp = $otp
    } | ConvertTo-Json

    $response = Invoke-RestMethod -Uri "$apiUrl/verify-otp" -Method Post -Body $body -ContentType "application/json"
    Write-Host "✓ OTP verified successfully!" -ForegroundColor Green
    Write-Host "Message: $($response.message)" -ForegroundColor Gray
} catch {
    Write-Host "✗ OTP verification failed!" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    if ($_.ErrorDetails.Message) {
        $errorObj = $_.ErrorDetails.Message | ConvertFrom-Json
        Write-Host "Error: $($errorObj.error)" -ForegroundColor Red
    }
}
Write-Host ""

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Complete!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Press any key to exit..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
