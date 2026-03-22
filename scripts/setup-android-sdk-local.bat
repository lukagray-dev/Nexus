@echo off
setlocal

set "ROOT_DIR=%~dp0.."
set "SDK_ROOT=%ROOT_DIR%\.android-sdk"
set "TOOLS_DIR=%SDK_ROOT%\cmdline-tools\latest"
set "CACHE_DIR=%ROOT_DIR%\.cache"
set "TOOLS_ZIP=%CACHE_DIR%\android-cmdline-tools.zip"
set "TOOLS_URL=https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip"
set "JDK_DIR=%ROOT_DIR%\.jdks\jdk-21"

if not exist "%JDK_DIR%\bin\java.exe" (
  echo [ERROR] JDK 21 not found at "%JDK_DIR%".
  echo [HINT] Run scripts\setup-java21-local.bat first.
  exit /b 1
)

set "JAVA_HOME=%JDK_DIR%"
set "PATH=%JAVA_HOME%\bin;%PATH%"

if exist "%TOOLS_DIR%\bin\sdkmanager.bat" goto :install_packages

echo [INFO] Downloading Android command-line tools...
if not exist "%CACHE_DIR%" mkdir "%CACHE_DIR%"
if not exist "%SDK_ROOT%" mkdir "%SDK_ROOT%"

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -Uri '%TOOLS_URL%' -OutFile '%TOOLS_ZIP%'"
if errorlevel 1 (
  echo [ERROR] Failed to download Android command-line tools.
  exit /b 1
)

echo [INFO] Extracting Android command-line tools...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$sdk='%SDK_ROOT%'; $tmp='$sdk\\.tmp-tools'; if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }; Expand-Archive -Path '%TOOLS_ZIP%' -DestinationPath $tmp -Force; New-Item -ItemType Directory -Force -Path '$sdk\\cmdline-tools\\latest' | Out-Null; Copy-Item -Path '$tmp\\cmdline-tools\\*' -Destination '$sdk\\cmdline-tools\\latest' -Recurse -Force; Remove-Item -Recurse -Force $tmp"
if errorlevel 1 (
  echo [ERROR] Failed to extract Android command-line tools.
  exit /b 1
)

:install_packages
set "ANDROID_SDK_ROOT=%SDK_ROOT%"
set "ANDROID_HOME=%SDK_ROOT%"
set "PATH=%TOOLS_DIR%\bin;%PATH%"

echo [INFO] Installing required Android SDK packages...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$env:JAVA_HOME='%JDK_DIR%'; $env:ANDROID_SDK_ROOT='%SDK_ROOT%'; $env:ANDROID_HOME='%SDK_ROOT%'; $env:Path='%TOOLS_DIR%\\bin;' + $env:Path; 1..200 | ForEach-Object { 'y' } | & sdkmanager.bat --sdk_root=$env:ANDROID_SDK_ROOT 'platform-tools' 'platforms;android-36' 'build-tools;36.0.0'"
if errorlevel 1 (
  echo [ERROR] Failed to install Android SDK packages.
  exit /b 1
)

echo [OK] Android SDK ready at "%SDK_ROOT%"
exit /b 0
