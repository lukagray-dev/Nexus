@echo off
REM ==============================================================================
REM NEXUS DESKTOP - CONVENIENT ROOT LAUNCHER
REM ==============================================================================
REM This script is a lightweight alias that delegates execution to
REM run-parent-desktop.bat, ensuring easy one-click launching from the scripts folder.
REM ==============================================================================

setlocal
set "SCRIPT_DIR=%~dp0"
call "%SCRIPT_DIR%run-parent-desktop.bat" %*
exit /b %ERRORLEVEL%
