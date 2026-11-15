@echo off
setlocal
echo ========================================================
echo        Building AetherCam NSIS Windows Installer
echo ========================================================

where makensis >nul 2>&1
if %errorLevel% NEQ 0 (
    echo [ERROR] NSIS (makensis.exe) not found in system PATH.
    echo Please install NSIS from https://nsis.sourceforge.io/ or via Chocolatey: choco install nsis
    pause
    exit /b 1
)

echo Compiling AetherCam_Installer.nsi...
makensis "%~dp0AetherCam_Installer.nsi"

if %errorLevel% EQU 0 (
    echo.
    echo [SUCCESS] AetherCam Setup EXE generated at:
    echo %~dp0..\AetherCam_Setup_v2.1.0_x64.exe
) else (
    echo [ERROR] NSIS build failed.
)

endlocal
pause
