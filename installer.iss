; ─────────────────────────────────────────────────────────────────────────────
; Stock Manager — Inno Setup Installer Script
; Builds: StockManager-Setup-1.0.1.exe
; ─────────────────────────────────────────────────────────────────────────────

#define AppName      "Stock Manager"
#ifndef AppVersion
  #define AppVersion "1.0.1"
#endif
#define AppPublisher "Collomut"
#define AppURL       "https://github.com/Collomut/StoreUpdater"
#define AppExeName   "StockManager.exe"
#define AppDir       "dist\StockManager"

[Setup]
AppId={{A3F2E1B4-9C7D-4E2A-B8F3-1D6E5A0C9B2F}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#AppPublisher}
AppPublisherURL={#AppURL}
AppSupportURL={#AppURL}
AppUpdatesURL={#AppURL}/releases
DefaultDirName={localappdata}\{#AppName}
DefaultGroupName={#AppName}
AllowNoIcons=no
LicenseFile=
OutputDir=dist-installer
OutputBaseFilename=StockManager-Setup-{#AppVersion}
SetupIconFile=src\main\resources\images\icon.ico
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
WizardSizePercent=120
DisableWelcomePage=no
DisableDirPage=no
DisableProgramGroupPage=no
UninstallDisplayIcon={app}\{#AppExeName}
UninstallDisplayName={#AppName}
VersionInfoVersion={#AppVersion}
VersionInfoCompany={#AppPublisher}
VersionInfoDescription={#AppName} Setup
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a &desktop shortcut"; GroupDescription: "Additional icons:"; Flags: checkedonce

[Files]
; Include the entire jpackage app-image (EXE + bundled JRE + app JAR)
Source: "{#AppDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#AppName}";       Filename: "{app}\{#AppExeName}"
Name: "{group}\Uninstall {#AppName}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExeName}"; Description: "Launch {#AppName}"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
Type: filesandordirs; Name: "{app}"

[Messages]
WelcomeLabel1=Welcome to the {#AppName} Setup Wizard
WelcomeLabel2=This will install {#AppName} version {#AppVersion} on your computer.%n%nNo Java installation is required — the Java runtime is included.%n%nClick Next to continue.
FinishedHeadingLabel=Setup Complete
FinishedLabel={#AppName} has been successfully installed.%n%nClick Finish to launch the application.
