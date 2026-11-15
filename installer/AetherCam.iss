; =============================================================================
; AetherCam Studio  ·  Windows Installer
; Built with Inno Setup 6.x  (https://jrsoftware.org/isinfo.php)
; =============================================================================

#define AppName       "AetherCam Studio"
#define AppVersion    "2.1.0"
#define AppPublisher  "AetherCam Open Source Project"
#define AppURL        "https://github.com/bernardlolo25/AetherCam"
#define AppExeName    "AetherCam.exe"
#define AppID         "{{A5E9C3D1-7F42-4B8E-9C6A-2D3F8E1B4A07}"

[Setup]
; --- Identity ---
AppId={#AppID}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher={#AppPublisher}
AppPublisherURL={#AppURL}
AppSupportURL={#AppURL}/issues
AppUpdatesURL={#AppURL}/releases

; --- Install location ---
DefaultDirName={autopf}\AetherCam
DefaultGroupName=AetherCam
AllowNoIcons=yes

; --- Output ---
OutputDir=..\dist
OutputBaseFilename=AetherCam_Setup_{#AppVersion}_x64
SetupIconFile=assets\icon.ico

; --- Compression ---
Compression=lzma2/ultra64
SolidCompression=yes
LZMAUseSeparateProcess=yes

; --- Privileges ---
PrivilegesRequired=admin
PrivilegesRequiredOverridesAllowed=dialog

; --- Windows version requirements (Windows 10+) ---
MinVersion=10.0
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64

; --- Uninstall ---
UninstallDisplayIcon={app}\{#AppExeName}
UninstallDisplayName={#AppName} {#AppVersion}

; --- Misc ---
WizardStyle=modern
WizardSizePercent=120
DisableProgramGroupPage=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a &desktop shortcut"; GroupDescription: "Additional icons:"
Name: "startupicon"; Description: "Launch AetherCam when Windows &starts"; GroupDescription: "Startup:"; Flags: unchecked

[Files]
; ── Main application ─────────────────────────────────────────────────────────
Source: "..\bin\AetherCam.exe"; DestDir: "{app}"; Flags: ignoreversion

; ── Web UI assets (bundled React dashboard) ──────────────────────────────────
Source: "..\bin\dist\*"; DestDir: "{app}\dist"; Flags: ignoreversion recursesubdirs createallsubdirs

; ── DirectShow virtual camera driver ─────────────────────────────────────────
Source: "..\driver\virtual_camera\build64\Release\AetherCamVirtualCam.dll"; DestDir: "{app}\drivers"; Flags: ignoreversion
Source: "..\driver\virtual_camera\build\Release\AetherCamVirtualCam.dll"; DestDir: "{app}\drivers\x86"; Flags: ignoreversion

; ── DirectShow virtual microphone driver ─────────────────────────────────────
Source: "..\driver\virtual_mic\build64\Release\AetherCamVirtualMic.dll"; DestDir: "{app}\drivers"; Flags: ignoreversion
Source: "..\driver\virtual_mic\build\Release\AetherCamVirtualMic.dll"; DestDir: "{app}\drivers\x86"; Flags: ignoreversion

; ── Visual C++ Redistributable (bundled) ─────────────────────────────────────
Source: "redist\vc_redist.x64.exe"; DestDir: "{tmp}"; Flags: deleteafterinstall

; ── Driver install helper script & batch files ──────────────────────────────
Source: "driver_install_helper.ps1"; DestDir: "{app}"; Flags: ignoreversion
Source: "install_drivers.bat"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
; Start Menu
Name: "{group}\{#AppName}"; Filename: "{app}\{#AppExeName}"
Name: "{group}\Uninstall {#AppName}"; Filename: "{uninstallexe}"

; Desktop
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExeName}"; Tasks: desktopicon

; Startup
Name: "{userstartup}\{#AppName}"; Filename: "{app}\{#AppExeName}"; Tasks: startupicon

[Registry]
Root: HKLM; Subkey: "Software\AetherCam"; ValueType: string; ValueName: "InstallDir"; ValueData: "{app}"; Flags: uninsdeletekey
Root: HKLM; Subkey: "Software\AetherCam"; ValueType: string; ValueName: "Version"; ValueData: "{#AppVersion}"
Root: HKLM; Subkey: "Software\AetherCam"; ValueType: dword; ValueName: "FirewallConfigured"; ValueData: "1"

[Run]
; 1. Install Visual C++ Runtime silently
Filename: "{tmp}\vc_redist.x64.exe"; Parameters: "/install /quiet /norestart"; StatusMsg: "Installing Visual C++ Runtime..."; Flags: waituntilterminated

; 2. Register DirectShow Virtual Camera Filter (CLSID_VideoInputDeviceCategory)
Filename: "{sys}\regsvr32.exe"; Parameters: "/s ""{app}\drivers\AetherCamVirtualCam.dll"""; StatusMsg: "Registering AetherCam Virtual Camera..."; Flags: runhidden waituntilterminated

; 3. Register DirectShow Virtual Microphone Filter (CLSID_AudioInputDeviceCategory)
Filename: "{sys}\regsvr32.exe"; Parameters: "/s ""{app}\drivers\AetherCamVirtualMic.dll"""; StatusMsg: "Registering AetherCam Virtual Microphone..."; Flags: runhidden waituntilterminated

; 4. Add Windows Firewall rules for UDP media stream and TCP control plane
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall add rule name=""AetherCam UDP Stream"" dir=in action=allow protocol=UDP localport=48200 profile=private,domain"; StatusMsg: "Configuring Firewall (UDP 48200)..."; Flags: runhidden waituntilterminated
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall add rule name=""AetherCam TCP Control"" dir=in action=allow protocol=TCP localport=48201 profile=private,domain"; StatusMsg: "Configuring Firewall (TCP 48201)..."; Flags: runhidden waituntilterminated

; 5. Launch AetherCam after installation (optional checkbox)
Filename: "{app}\{#AppExeName}"; Description: "Launch AetherCam Studio now"; Flags: nowait postinstall skipifsilent

[UninstallRun]
; Unregister DirectShow filters
Filename: "{sys}\regsvr32.exe"; Parameters: "/u /s ""{app}\drivers\AetherCamVirtualCam.dll"""; Flags: runhidden waituntilterminated
Filename: "{sys}\regsvr32.exe"; Parameters: "/u /s ""{app}\drivers\AetherCamVirtualMic.dll"""; Flags: runhidden waituntilterminated

; Remove Firewall rules
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall delete rule name=""AetherCam UDP Stream"""; Flags: runhidden waituntilterminated
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall delete rule name=""AetherCam TCP Control"""; Flags: runhidden waituntilterminated

[UninstallDelete]
Type: filesandordirs; Name: "{app}"
