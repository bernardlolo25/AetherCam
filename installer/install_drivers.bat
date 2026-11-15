@echo off
:: AetherCam Driver Installer & Firewall Configuration
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo Requesting Administrator privileges...
    powershell -Command "Start-Process '%~f0' -Verb RunAs"
    exit /b
)

set "SCRIPT_DIR=%~dp0"
echo [*] Registering AetherCam Virtual Camera...
if exist "%SCRIPT_DIR%drivers\driver\virtual_camera\build64\Release\AetherCamVirtualCam.dll" (
    regsvr32.exe /s "%SCRIPT_DIR%drivers\driver\virtual_camera\build64\Release\AetherCamVirtualCam.dll"
) else if exist "%SCRIPT_DIR%AetherCamVirtualCam.dll" (
    regsvr32.exe /s "%SCRIPT_DIR%AetherCamVirtualCam.dll"
)

echo [*] Registering AetherCam Virtual Microphone...
if exist "%SCRIPT_DIR%drivers\driver\virtual_mic\build64\Release\AetherCamVirtualMic.dll" (
    regsvr32.exe /s "%SCRIPT_DIR%drivers\driver\virtual_mic\build64\Release\AetherCamVirtualMic.dll"
) else if exist "%SCRIPT_DIR%AetherCamVirtualMic.dll" (
    regsvr32.exe /s "%SCRIPT_DIR%AetherCamVirtualMic.dll"
)

echo [*] Configuring Windows Defender Firewall...
netsh advfirewall firewall add rule name="AetherCam UDP Stream" dir=in action=allow protocol=UDP localport=48200 profile=private,domain >nul 2>&1
netsh advfirewall firewall add rule name="AetherCam TCP Control" dir=in action=allow protocol=TCP localport=48201 profile=private,domain >nul 2>&1

echo.
echo [+] Done! "AetherCam Virtual Camera" is ready for Zoom, OBS, Teams, Meet, and Discord.
pause
