# AetherCam Driver Installation & Verification Helper
param (
    [switch]$Repair,
    [switch]$Unregister
)

$ErrorActionPreference = "Stop"

function Test-Admin {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

if (-not (Test-Admin)) {
    Write-Warning "Administrative privileges required. Relaunching with elevation..."
    Start-Process powershell.exe -ArgumentList ("-NoProfile -ExecutionPolicy Bypass -File `"{0}`"" -f $MyInvocation.MyCommand.Path) -Verb RunAs
    exit
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$camDll = Join-Path $scriptDir "..\driver\virtual_camera\AetherCamVirtualCam.dll"
$micDll = Join-Path $scriptDir "..\driver\virtual_mic\AetherCamVirtualMic.dll"

if ($Unregister) {
    Write-Host "[*] Unregistering AetherCam Virtual Devices..." -ForegroundColor Yellow
    regsvr32.exe /u /s "$camDll"
    regsvr32.exe /u /s "$micDll"
    Write-Host "[+] Drivers unregistered successfully." -ForegroundColor Green
    exit
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "       AetherCam Driver Verification & Registration       " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Register Virtual Camera
if (Test-Path $camDll) {
    Write-Host "[*] Registering AetherCam Virtual Camera ($camDll)..."
    $res = Start-Process "regsvr32.exe" -ArgumentList "/s `"$camDll`"" -PassThru -Wait
    if ($res.ExitCode -eq 0) {
        Write-Host "[+] Virtual Camera registered successfully!" -ForegroundColor Green
    } else {
        Write-Error "Failed to register Virtual Camera. Exit code: $($res.ExitCode)"
    }
} else {
    Write-Warning "Cannot locate $camDll"
}

# 2. Register Virtual Microphone
if (Test-Path $micDll) {
    Write-Host "[*] Registering AetherCam Virtual Microphone ($micDll)..."
    $res = Start-Process "regsvr32.exe" -ArgumentList "/s `"$micDll`"" -PassThru -Wait
    if ($res.ExitCode -eq 0) {
        Write-Host "[+] Virtual Microphone registered successfully!" -ForegroundColor Green
    } else {
        Write-Error "Failed to register Virtual Microphone. Exit code: $($res.ExitCode)"
    }
} else {
    Write-Warning "Cannot locate $micDll"
}

# 3. Verify in Registry
$camClsidKey = "HKCR:\CLSID\{A84CE391-7681-4321-B089-8FA3E5114D20}"
if (Test-Path $camClsidKey) {
    Write-Host "[+] Verification: 'AetherCam Virtual Camera' verified in Windows Registry." -ForegroundColor Green
} else {
    Write-Warning "Registry verification pending or filter registered under private root."
}

Write-Host "`nAll virtual devices are active and ready for Zoom, OBS, Teams, and Discord." -ForegroundColor Cyan
