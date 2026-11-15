@echo off
:: Elevated batch script to register AetherCam Virtual Camera
:: Checks for admin permissions
net session >nul 2>&1
if %errorLevel% NEQ 0 (
    echo [ERROR] Administrative privileges required.
    echo Right-click this script and select 'Run as Administrator'.
    pause
    exit /b 1
)

set DLL_PATH=%~dp0AetherCamVirtualCam.dll
if not exist "%DLL_PATH%" (
    echo [ERROR] Cannot locate AetherCamVirtualCam.dll at %DLL_PATH%
    pause
    exit /b 1
)

echo Registering AetherCam Virtual Camera...
regsvr32.exe /s "%DLL_PATH%"

if %errorLevel% EQU 0 (
    echo [SUCCESS] AetherCam Virtual Camera successfully registered!
    echo It is now available in Zoom, OBS Studio, Microsoft Teams, Discord, Google Meet, and Chrome.
) else (
    echo [ERROR] Failed to register virtual camera. Error code: %errorLevel%
)

pause
