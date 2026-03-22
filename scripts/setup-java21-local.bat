@echo off
setlocal

set "ROOT_DIR=%~dp0.."
set "JDK_DIR=%ROOT_DIR%\.jdks\jdk-21"
set "CACHE_DIR=%ROOT_DIR%\.cache"
set "JDK_ZIP=%CACHE_DIR%\jdk21.zip"
set "JDK_URL=https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.9%%2B10/OpenJDK21U-jdk_x64_windows_hotspot_21.0.9_10.zip"

if exist "%JDK_DIR%\bin\java.exe" (
  echo [INFO] JDK 21 already installed at "%JDK_DIR%"
  goto :verify
)

echo [INFO] Downloading JDK 21...
if not exist "%CACHE_DIR%" mkdir "%CACHE_DIR%"

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -Uri '%JDK_URL%' -OutFile '%JDK_ZIP%'"
if errorlevel 1 (
  echo [ERROR] Failed to download JDK 21.
  exit /b 1
)

echo [INFO] Extracting JDK 21...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$dest='%ROOT_DIR%\\.jdks'; $tmp='$dest\\.tmp-jdk'; if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }; New-Item -ItemType Directory -Force -Path $dest | Out-Null; Expand-Archive -Path '%JDK_ZIP%' -DestinationPath $tmp -Force; $folder = Get-ChildItem -Path $tmp -Directory | Where-Object { $_.Name -like 'jdk-21*' } | Select-Object -First 1; if (-not $folder) { throw 'Could not find extracted JDK folder.' }; if (Test-Path '%JDK_DIR%') { Remove-Item -Recurse -Force '%JDK_DIR%' }; Move-Item -Path $folder.FullName -Destination '%JDK_DIR%'; Remove-Item -Recurse -Force $tmp"
if errorlevel 1 (
  echo [ERROR] Failed to extract JDK 21.
  exit /b 1
)

:verify
set "JAVA_HOME=%JDK_DIR%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
java --version
if errorlevel 1 (
  echo [ERROR] Installed JDK verification failed.
  exit /b 1
)

echo [OK] JDK 21 ready at "%JDK_DIR%"
exit /b 0
