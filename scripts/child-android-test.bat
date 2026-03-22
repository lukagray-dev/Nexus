@echo off
setlocal

set "ROOT_DIR=%~dp0.."
set "ANDROID_DIR=%ROOT_DIR%\Nexus Child\android"
set "JAVA_HOME=%ROOT_DIR%\.jdks\jdk-21"
set "ANDROID_SDK_ROOT=%ROOT_DIR%\.android-sdk"
set "ANDROID_HOME=%ANDROID_SDK_ROOT%"
set "ANDROID_USER_HOME=%ROOT_DIR%\.android-user-home"
set "GRADLE_USER_HOME=%ROOT_DIR%\.gradle-user-home"
set "PATH=%JAVA_HOME%\bin;%PATH%"

if not exist "%ANDROID_DIR%\gradlew.bat" (
  echo [ERROR] Android project not found: "%ANDROID_DIR%"
  exit /b 1
)

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo [ERROR] JDK 21 not found at "%JAVA_HOME%"
  exit /b 1
)

if not exist "%ANDROID_SDK_ROOT%" (
  echo [ERROR] Android SDK not found at "%ANDROID_SDK_ROOT%"
  exit /b 1
)

cd /d "%ANDROID_DIR%"

echo [INFO] Running Child Android unit tests...
call gradlew.bat testDebugUnitTest --no-daemon
exit /b %errorlevel%
