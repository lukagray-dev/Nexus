@echo off
REM ============================================================
REM Android SDK Setup Script
REM ============================================================
REM This script sets up the Android SDK with required tools,
REM build-tools, and platform packages.
REM ============================================================

setlocal enabledelayedexpansion
set "ANDROID_SDK_ROOT=C:\Users\%USERNAME%\AppData\Local\Android\Sdk"

echo.
echo ============================================================
echo Android SDK Setup
echo ============================================================
echo.

REM Check if SDK directory exists
if not exist "%ANDROID_SDK_ROOT%" (
  echo [ERROR] Android SDK not found at:
  echo         %ANDROID_SDK_ROOT%
  echo.
  echo [INFO] Please install Android SDK first from:
  echo        https://developer.android.com/studio
  echo.
  exit /b 1
)

echo [INFO] Android SDK root: %ANDROID_SDK_ROOT%
echo.

REM Set up licenses directory
echo [STEP 1/3] Setting up SDK licenses...
set "LICENSES_DIR=%ANDROID_SDK_ROOT%\licenses"
if not exist "%LICENSES_DIR%" (
  mkdir "%LICENSES_DIR%"
  echo [INFO] Created licenses directory
)

REM Write license acceptance hashes
(
  echo 24333f8a63b6825ea9c5514f83c2829b004d1fee
) > "%LICENSES_DIR%\android-sdk-license"

(
  echo 601085b94cd77f0b54ff86406957099bc1d16b63
) > "%LICENSES_DIR%\android-sdk-preview-license"

(
  echo 84831b9409646a918e30573bab4c9c91346d8abd
) > "%LICENSES_DIR%\google-play-services-license"

echo [OK] Licenses accepted.
echo.

REM Try to use sdkmanager if available
echo [STEP 2/3] Checking for sdkmanager...
set "SDKMANAGER=%ANDROID_SDK_ROOT%\cmdline-tools\bin\sdkmanager.bat"

if exist "%SDKMANAGER%" (
  echo [INFO] Found sdkmanager at: %SDKMANAGER%
  echo.
  echo [ACTION] Installing Android SDK components...
  echo          (This may take several minutes)
  echo.
  
  REM Try to install with sdkmanager
  call "%SDKMANAGER%" --sdk_root="%ANDROID_SDK_ROOT%" ^
    "platform-tools" ^
    "platforms;android-35" ^
    "platforms;android-36" ^
    "build-tools;35.0.0" ^
    "build-tools;36.0.0" ^
    "cmdline-tools;latest"
    
  if errorlevel 0 (
    echo [OK] SDK components installed.
  ) else (
    echo [WARN] sdkmanager failed. You may need to install components manually.
  )
) else (
  echo [WARN] sdkmanager not found.
  echo [INFO] Expected location: %SDKMANAGER%
  echo [INFO] Please install components using Android Studio or manually download:
  echo        - build-tools;35.0.0
  echo        - build-tools;36.0.0
  echo        - platforms;android-35
  echo        - platforms;android-36
)

echo.
echo [STEP 3/3] Verifying setup...

REM Check for required components
set "MISSING_COMPONENTS=0"

if not exist "%ANDROID_SDK_ROOT%\build-tools\35.0.0" (
  echo [WARN] Missing: build-tools;35.0.0
  set "MISSING_COMPONENTS=1"
) else (
  echo [OK] Found: build-tools;35.0.0
)

if not exist "%ANDROID_SDK_ROOT%\build-tools\36.0.0" (
  echo [WARN] Missing: build-tools;36.0.0
  set "MISSING_COMPONENTS=1"
) else (
  echo [OK] Found: build-tools;36.0.0
)

if not exist "%ANDROID_SDK_ROOT%\platforms\android-35" (
  echo [WARN] Missing: platforms;android-35
  set "MISSING_COMPONENTS=1"
) else (
  echo [OK] Found: platforms;android-35
)

if not exist "%ANDROID_SDK_ROOT%\platforms\android-36" (
  echo [WARN] Missing: platforms;android-36
  set "MISSING_COMPONENTS=1"
) else (
  echo [OK] Found: platforms;android-36
)

echo.

if %MISSING_COMPONENTS% equ 1 (
  echo [WARN] Some SDK components are missing.
  echo [INFO] You can download them manually using Android Studio:
  echo        1. Open Android Studio
  echo        2. Go to Tools ^> SDK Manager
  echo        3. Install the missing components:
  echo           - Android SDK Build-Tools 35
  echo           - Android SDK Build-Tools 36
  echo           - Android 13 (API 33)
  echo           - Android 14 (API 34)
  echo           - Android 15 (API 36)
  echo.
  exit /b 0
) else (
  echo ============================================================
  echo [SUCCESS] Android SDK setup complete!
  echo ============================================================
  echo.
  echo You can now run the installation scripts:
  echo   - Install-Nexus-Child.bat
  echo   - Install-Nexus-Parent.bat
  echo.
  exit /b 0
)
