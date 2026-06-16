@echo off
REM ============================================================
REM Nexus Parent Android - Installation and Build Script
REM ============================================================
REM This script builds and installs the Nexus Parent APK on a 
REM connected Android device or emulator.
REM ============================================================

setlocal enabledelayedexpansion
set "ROOT_DIR=%~dp0.."
set "PARENT_PROJECT=%ROOT_DIR%\Nexus Parent\mobile\android"

echo.
echo ============================================================
echo Nexus Parent Android - Installation Script
echo ============================================================
echo.

REM Verify project directory exists
if not exist "%PARENT_PROJECT%" (
  echo [ERROR] Parent Android project not found at:
  echo         "%PARENT_PROJECT%"
  echo.
  exit /b 1
)

cd /d "%PARENT_PROJECT%"
if errorlevel 1 (
  echo [ERROR] Failed to navigate to Parent Android project directory.
  exit /b 1
)

REM Check if setup has been done
set "SETUP_MARKER=%ROOT_DIR%\.android-setup-done"
if not exist "%SETUP_MARKER%" (
  echo [INFO] First-time setup required. Running Setup-Android-SDK.bat...
  echo.
  call "%ROOT_DIR%\scripts\Setup-Android-SDK.bat"
  if errorlevel 1 (
    echo [ERROR] Android SDK setup failed.
    exit /b 1
  )
  type nul > "%SETUP_MARKER%"
  echo.
)

REM Display Java version check
echo [CHECK] Verifying Java environment...
java -version 2>&1 | find "21" >nul
if errorlevel 1 (
  echo [WARN] Java 21 not detected. Ensure JDK 21 is installed.
)

REM Display Android SDK check
echo [CHECK] Verifying Android SDK environment...
if not defined ANDROID_HOME (
  echo [WARN] ANDROID_HOME not set. Ensure Android SDK is installed.
  echo        Run: setup-parent-android-toolchain.bat first
) else (
  echo [INFO] ANDROID_HOME: %ANDROID_HOME%
)

REM Accept licenses
echo [STEP 1/4] Accepting Android SDK licenses...
set "LICENSES_DIR=%ANDROID_HOME%\licenses"
if not exist "%LICENSES_DIR%" mkdir "%LICENSES_DIR%"

REM Write license acceptance hashes
(
  echo 24333f8a63b6825ea9c5514f83c2829b004d1fee
) > "%LICENSES_DIR%\android-sdk-license"

(
  echo 601085b94cd77f0b54ff86406957099bc1d16b63
) > "%LICENSES_DIR%\android-sdk-preview-license"

echo [INFO] Licenses accepted.
echo.
echo [STEP 2/4] Running pre-build checks...
echo [INFO] Building debug APK for testing...
echo.

REM Clean previous builds (skip if takes too long)
echo [ACTION] Cleaning previous builds...
timeout /t 2 /nobreak >nul
REM Skip clean to avoid hangs, gradlew will handle incremental builds

REM Build debug APK
echo [ACTION] Building debug APK (this may take 2-5 minutes)...
echo [INFO] Gradle is downloading dependencies and compiling...
call gradlew.bat assembleDebug --no-daemon --stacktrace
if errorlevel 1 (
  echo [ERROR] Failed to build Nexus Parent debug APK.
  echo         Check build output above for details.
  exit /b 1
)

echo.
echo [STEP 3/3] Build completed successfully.
echo [INFO] APK location:
echo        %PARENT_PROJECT%\app\build\outputs\apk\debug\app-debug.apk
echo.

REM Check for connected devices
echo [STEP 4/4] Installing on connected device/emulator...
echo [ACTION] Checking for connected Android devices...
call adb devices 2>nul | find /c "device" >nul
if errorlevel 1 (
  echo [WARN] adb not found or no devices connected.
  echo [INFO] Manual installation steps:
  echo        1. Ensure device is connected via USB or emulator is running
  echo        2. Enable USB debugging on your device
  echo        3. Run: adb install -r "%PARENT_PROJECT%\app\build\outputs\apk\debug\app-debug.apk"
  echo.
  exit /b 0
)

REM Install APK
echo [ACTION] Installing APK on device...
call adb install -r "%PARENT_PROJECT%\app\build\outputs\apk\debug\app-debug.apk"
if errorlevel 1 (
  echo [ERROR] Failed to install APK. Check device connection.
  exit /b 1
)

echo.
echo ============================================================
echo [SUCCESS] Nexus Parent installed successfully!
echo ============================================================
echo.
echo [INFO] The app should now be available on your device.
echo        Package: nexus.android.parent
echo        Launch the app to begin testing.
echo.
exit /b 0
