@echo off
setlocal

set "ROOT_DIR=%~dp0.."
set "ANDROID_PROJECT=%ROOT_DIR%\Nexus Parent\mobile\android"
set "SDK_ROOT=%ROOT_DIR%\.android-sdk"

echo [STEP] Setting up local JDK 21...
call "%~dp0setup-java21-local.bat"
if errorlevel 1 exit /b 1

echo [STEP] Setting up local Android SDK...
call "%~dp0setup-android-sdk-local.bat"
if errorlevel 1 exit /b 1

if not exist "%ANDROID_PROJECT%" (
  echo [WARN] Android project not found at "%ANDROID_PROJECT%". Skipping local.properties update.
  exit /b 0
)

echo [STEP] Writing local.properties for Android project...
if not exist "%ANDROID_PROJECT%" exit /b 1
> "%ANDROID_PROJECT%\local.properties" echo sdk.dir=%SDK_ROOT:\=\\%

echo [OK] Android toolchain setup complete.
echo [INFO] local.properties -> "%ANDROID_PROJECT%\local.properties"
exit /b 0
