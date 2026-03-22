@echo off
setlocal

set "ROOT_DIR=%~dp0.."
set "DESKTOP_DIR=%ROOT_DIR%\Nexus Parent\desktop"

if not exist "%DESKTOP_DIR%\package.json" (
  echo [ERROR] Desktop project not found: "%DESKTOP_DIR%"
  exit /b 1
)

cd /d "%DESKTOP_DIR%"

if not exist "node_modules" (
  echo [INFO] Installing desktop dependencies...
  call npm.cmd install
  if errorlevel 1 exit /b 1
)

echo [INFO] Running desktop tests...
call npm.cmd test -- --run
exit /b %errorlevel%
