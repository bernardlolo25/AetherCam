; =============================================================================
; AetherCam Studio  ·  Windows Installer
; Built with Inno Setup 6.x  (https://jrsoftware.org/isinfo.php)
; =============================================================================
; What this installer does:
;   1. Installs AetherCam.exe  (standalone .NET 10 desktop server + React UI)
;   2. Installs DirectShow virtual camera DLL  (AetherCamVirtualCam.dll)
;   3. Installs DirectShow virtual microphone DLL (AetherCamVirtualMic.dll)
;   4. Runs regsvr32 to register both COM filters so apps see the devices
;   5. Adds Windows Defender Firewall rules  (UDP 48200, TCP 48201)
;   6. Creates Start-Menu folder + Desktop shortcut
;   7. Registers an Uninstall entry in Windows Settings → Apps
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

; --- Windows version requirements ---
MinVersion=10.0.17763    ; Windows 10 version 1809+
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
Name: "desktopicon"; Description: "Create a &desktop shortcut"; GroupDescription: "Additional icons:"; Flags: checked
Name: "startupicon"; Description: "Launch AetherCam when Windows &starts"; GroupDescription: "Startup:"; Flags: unchecked

[Files]
; ── Main application ─────────────────────────────────────────────────────────
Source: "..\bin\AetherCam.exe";            DestDir: "{app}"; Flags: ignoreversion

; ── Web UI assets (bundled React dashboard) ──────────────────────────────────
Source: "..\bin\dist\*";                   DestDir: "{app}\dist"; Flags: ignoreversion recursesubdirs createallsubdirs

; ── DirectShow virtual camera driver ─────────────────────────────────────────
Source: "..\driver\virtual_camera\build\Release\AetherCamVirtualCam.dll";
  DestDir: "{sys}"; Flags: ignoreversion regserver 32bit; Check: not IsWin64
Source: "..\driver\virtual_camera\build\Release\AetherCamVirtualCam.dll";
  DestDir: "{syswow64}"; Flags: ignoreversion; Check: IsWin64
Source: "..\driver\virtual_camera\build64\Release\AetherCamVirtualCam.dll";
  DestDir: "{sys}"; Flags: ignoreversion regserver; Check: IsWin64

; ── DirectShow virtual microphone driver ─────────────────────────────────────
Source: "..\driver\virtual_mic\build\Release\AetherCamVirtualMic.dll";
  DestDir: "{sys}"; Flags: ignoreversion regserver 32bit; Check: not IsWin64
Source: "..\driver\virtual_mic\build\Release\AetherCamVirtualMic.dll";
  DestDir: "{syswow64}"; Flags: ignoreversion; Check: IsWin64
Source: "..\driver\virtual_mic\build64\Release\AetherCamVirtualMic.dll";
  DestDir: "{sys}"; Flags: ignoreversion regserver; Check: IsWin64

; ── Visual C++ Redistributable (bundled so users don't need to install it) ───
Source: "redist\vc_redist.x64.exe"; DestDir: "{tmp}"; Flags: deleteafterinstall

; ── Driver install helper PowerShell script ───────────────────────────────────
Source: "..\installer\driver_install_helper.ps1"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
; Start Menu
Name: "{group}\{#AppName}";           Filename: "{app}\{#AppExeName}"
Name: "{group}\Uninstall {#AppName}"; Filename: "{uninstallexe}"

; Desktop (optional task)
Name: "{autodesktop}\{#AppName}";     Filename: "{app}\{#AppExeName}"; Tasks: desktopicon

; Startup (optional task)
Name: "{userstartup}\{#AppName}";     Filename: "{app}\{#AppExeName}"; Tasks: startupicon

[Registry]
; App registration
Root: HKLM; Subkey: "Software\AetherCam";
  ValueType: string; ValueName: "InstallDir"; ValueData: "{app}"; Flags: uninsdeletekey
Root: HKLM; Subkey: "Software\AetherCam";
  ValueType: string; ValueName: "Version"; ValueData: "{#AppVersion}"

; Allow firewall access marker
Root: HKLM; Subkey: "Software\AetherCam";
  ValueType: dword; ValueName: "FirewallConfigured"; ValueData: "1"

[Run]
; ── 1. Install Visual C++ Redistributable if needed ──────────────────────────
Filename: "{tmp}\vc_redist.x64.exe";
  Parameters: "/install /quiet /norestart";
  StatusMsg: "Installing Visual C++ Runtime...";
  Flags: waituntilterminated

; ── 2. Add Firewall rules (silent, errors ignored) ───────────────────────────
Filename: "{sys}\netsh.exe";
  Parameters: "advfirewall firewall add rule name=""AetherCam UDP Stream"" dir=in action=allow protocol=UDP localport=48200 profile=private,domain";
  StatusMsg: "Configuring Windows Firewall (UDP 48200)...";
  Flags: runhidden waituntilterminated

Filename: "{sys}\netsh.exe";
  Parameters: "advfirewall firewall add rule name=""AetherCam TCP Control"" dir=in action=allow protocol=TCP localport=48201 profile=private,domain";
  StatusMsg: "Configuring Windows Firewall (TCP 48201)...";
  Flags: runhidden waituntilterminated

; ── 3. Launch AetherCam after install (optional) ─────────────────────────────
Filename: "{app}\{#AppExeName}";
  Description: "Launch AetherCam Studio now";
  Flags: nowait postinstall skipifsilent

[UninstallRun]
; Remove Firewall rules on uninstall
Filename: "{sys}\netsh.exe";
  Parameters: "advfirewall firewall delete rule name=""AetherCam UDP Stream""";
  Flags: runhidden waituntilterminated

Filename: "{sys}\netsh.exe";
  Parameters: "advfirewall firewall delete rule name=""AetherCam TCP Control""";
  Flags: runhidden waituntilterminated

[UninstallDelete]
Type: filesandordirs; Name: "{app}"

[Code]
// ── Pre-install checks ──────────────────────────────────────────────────────

function IsDotNetInstalled(): Boolean;
var
  SubKey: String;
  Value:  String;
begin
  // Check for .NET 10 runtime
  SubKey := 'SOFTWARE\dotnet\Setup\InstalledVersions\x64\sharedhost';
  Result := RegQueryStringValue(HKLM, SubKey, 'Version', Value);
  if Result then
    Result := (Pos('10.', Value) = 1) or (Pos('9.', Value) = 1) or (Pos('8.', Value) = 1);
end;

function InitializeSetup(): Boolean;
begin
  Result := True;

  // Warn if on 32-bit Windows (we only ship x64)
  if not IsWin64 then
  begin
    MsgBox('AetherCam requires a 64-bit version of Windows 10 or later.' + #13#10 +
           'Setup cannot continue.', mbError, MB_OK);
    Result := False;
    Exit;
  end;

  // .NET runtime is embedded in our self-contained EXE, but show info
  if not IsDotNetInstalled then
    MsgBox('Note: AetherCam bundles its own .NET runtime.' + #13#10 +
           'No separate .NET installation is required.', mbInformation, MB_OK);
end;

// ── After install: show driver status ───────────────────────────────────────
procedure CurStepChanged(CurStep: TSetupStep);
var
  ResultCode: Integer;
begin
  if CurStep = ssPostInstall then
  begin
    // Optionally verify camera CLSID is registered
    if RegKeyExists(HKCR, 'CLSID\{A84CE391-7681-4321-B089-8FA3E5114D20}') then
      Log('AetherCam Virtual Camera CLSID verified in registry.')
    else
      Log('Virtual Camera CLSID not found — driver build may need to run first.');
  end;
end;
