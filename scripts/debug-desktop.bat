@echo off
REM ==============================================================================
REM NEXUS DESKTOP - DEBUG SCRIPT ALIAS
REM ==============================================================================
REM This script delegates execution to debug-parent-desktop.bat for VS Code integration.
REM ==============================================================================

setlocal
set "SCRIPT_DIR=%~dp0"
call "%SCRIPT_DIR%debug-parent-desktop.bat" %*
exit /b %ERRORLEVEL%
