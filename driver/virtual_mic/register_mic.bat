@echo off
:: Elevated batch script to register AetherCam Virtual Microphone
net session >nul 2>&1
if %errorLevel% NEQ 0 (
    echo [ERROR] Administrative privileges required.
    echo Right-click this script and select 'Run as Administrator'.
    pause
    exit /b 1
)

set DLL_PATH=%~dp0AetherCamVirtualMic.dll
if not exist "%DLL_PATH%" (
    echo [ERROR] Cannot locate AetherCamVirtualMic.dll at %DLL_PATH%
    pause
    exit /b 1
)

echo Registering AetherCam Virtual Microphone...
regsvr32.exe /s "%DLL_PATH%"

if %errorLevel% EQU 0 (
    echo [SUCCESS] AetherCam Virtual Microphone successfully registered!
    echo It is now available as an audio recording source in all Windows applications.
) else (
    echo [ERROR] Failed to register virtual microphone. Error code: %errorLevel%
)

pause
