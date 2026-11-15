; AetherCam Professional Windows NSIS Installer Script
; Packages Desktop Application, DirectShow Virtual Camera, Virtual Microphone, and Firewall Config

!include "MUI2.nsh"
!include "FileFunc.nsh"

Name "AetherCam Studio Pro"
OutFile "..\AetherCam_Setup_v2.1.0_x64.exe"
InstallDir "$PROGRAMFILES64\AetherCam"
InstallDirRegKey HKLM "Software\AetherCam" "Install_Dir"
RequestExecutionLevel admin

; UI Configuration
!define MUI_ABORTWARNING
!define MUI_ICON "..\desktop\src-tauri\icons\icon.ico"
!define MUI_UNICON "..\desktop\src-tauri\icons\icon.ico"

; Installer Pages
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "..\docs\DEPLOYMENT_GUIDE.md"
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

; Uninstaller Pages
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "English"

Section "AetherCam Core & Virtual Drivers" SecCore
    SetOutPath "$INSTDIR"

    ; Copy binaries
    File "..\desktop\src-tauri\target\release\aethercam-desktop.exe"
    File "..\driver\virtual_camera\AetherCamVirtualCam.dll"
    File "..\driver\virtual_mic\AetherCamVirtualMic.dll"

    ; Register DirectShow Virtual Camera Filter (CLSID_VideoInputDeviceCategory)
    DetailPrint "Registering AetherCam Virtual Camera DirectShow Filter..."
    ExecWait 'regsvr32.exe /s "$INSTDIR\AetherCamVirtualCam.dll"'

    ; Register Virtual Microphone Filter
    DetailPrint "Registering AetherCam Virtual Microphone Filter..."
    ExecWait 'regsvr32.exe /s "$INSTDIR\AetherCamVirtualMic.dll"'

    ; Configure Windows Firewall Rules for sub-20ms UDP media & TCP control
    DetailPrint "Configuring Windows Defender Firewall exceptions..."
    ExecWait 'netsh advfirewall firewall add rule name="AetherCam UDP Stream" dir=in action=allow protocol=UDP localport=48200'
    ExecWait 'netsh advfirewall firewall add rule name="AetherCam TCP Control" dir=in action=allow protocol=TCP localport=48201'

    ; Write registry entries
    WriteRegStr HKLM "Software\AetherCam" "Install_Dir" "$INSTDIR"
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\AetherCam" "DisplayName" "AetherCam Studio Pro"
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\AetherCam" "UninstallString" '"$INSTDIR\uninstall.exe"'
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\AetherCam" "DisplayVersion" "2.1.0"
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\AetherCam" "Publisher" "AetherCam Open Source Team"

    ; Create Shortcuts
    CreateDirectory "$SMPROGRAMS\AetherCam"
    CreateShortcut "$SMPROGRAMS\AetherCam\AetherCam Studio.lnk" "$INSTDIR\aethercam-desktop.exe"
    CreateShortcut "$SMPROGRAMS\AetherCam\Uninstall AetherCam.lnk" "$INSTDIR\uninstall.exe"
    CreateShortcut "$DESKTOP\AetherCam Studio.lnk" "$INSTDIR\aethercam-desktop.exe"

    ; Create Uninstaller
    WriteUninstaller "$INSTDIR\uninstall.exe"
SectionEnd

Section "Uninstall"
    ; Unregister DirectShow COM Filters
    DetailPrint "Unregistering DirectShow Virtual Camera and Microphone..."
    ExecWait 'regsvr32.exe /u /s "$INSTDIR\AetherCamVirtualCam.dll"'
    ExecWait 'regsvr32.exe /u /s "$INSTDIR\AetherCamVirtualMic.dll"'

    ; Remove Firewall Rules
    DetailPrint "Removing Firewall exceptions..."
    ExecWait 'netsh advfirewall firewall delete rule name="AetherCam UDP Stream"'
    ExecWait 'netsh advfirewall firewall delete rule name="AetherCam TCP Control"'

    ; Delete Files
    Delete "$INSTDIR\aethercam-desktop.exe"
    Delete "$INSTDIR\AetherCamVirtualCam.dll"
    Delete "$INSTDIR\AetherCamVirtualMic.dll"
    Delete "$INSTDIR\uninstall.exe"
    RMDir "$INSTDIR"

    ; Delete Shortcuts
    Delete "$SMPROGRAMS\AetherCam\AetherCam Studio.lnk"
    Delete "$SMPROGRAMS\AetherCam\Uninstall AetherCam.lnk"
    RMDir "$SMPROGRAMS\AetherCam"
    Delete "$DESKTOP\AetherCam Studio.lnk"

    ; Remove Registry Keys
    DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\AetherCam"
    DeleteRegKey HKLM "Software\AetherCam"
SectionEnd
