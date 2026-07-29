@echo off
setlocal

:: ─────────────────────────────────────────────────────────────────────────────
:: build_release.bat  —  Build & package Stock Manager into a standalone EXE
::                        and a Windows installer (.exe setup wizard)
:: Just double-click this file. No editing needed.
:: ─────────────────────────────────────────────────────────────────────────────

echo.
echo  =============================================
echo   Stock Manager  —  Release Builder
echo  =============================================
echo.

:: ── Locate Java ──────────────────────────────────────────────────────────────
set JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot
set JAVA_BIN=%JAVA_HOME%\bin

if not exist "%JAVA_BIN%\java.exe" (
    for /f "tokens=*" %%i in ('where java 2^>nul') do (
        set JAVA_BIN=%%~dpi
        goto :java_found
    )
    echo [ERROR] Java not found. Please install JDK 21 or set JAVA_HOME.
    pause & exit /b 1
)
:java_found

:: ── Locate Maven ─────────────────────────────────────────────────────────────
set MVN_CMD=
if exist "C:\tools\apache-maven-3.9.6\bin\mvn.cmd"  set MVN_CMD=C:\tools\apache-maven-3.9.6\bin\mvn.cmd
if exist "C:\tools\apache-maven-3.9.6\bin\mvn.bat"  set MVN_CMD=C:\tools\apache-maven-3.9.6\bin\mvn.bat
if "%MVN_CMD%"=="" (
    for /f "tokens=*" %%i in ('where mvn 2^>nul') do ( set MVN_CMD=%%i & goto :mvn_found )
    echo [ERROR] Maven not found.
    pause & exit /b 1
)
:mvn_found

:: ── Locate Inno Setup compiler ───────────────────────────────────────────────
set ISCC=
if exist "%LOCALAPPDATA%\Programs\Inno Setup 6\ISCC.exe" set ISCC=%LOCALAPPDATA%\Programs\Inno Setup 6\ISCC.exe
if exist "C:\Program Files (x86)\Inno Setup 6\ISCC.exe"  set ISCC=C:\Program Files (x86)\Inno Setup 6\ISCC.exe
if exist "C:\Program Files\Inno Setup 6\ISCC.exe"        set ISCC=C:\Program Files\Inno Setup 6\ISCC.exe

:: ── Read version from version.properties ─────────────────────────────────────
set APP_VERSION=1.0.1
for /f "tokens=2 delims==" %%v in ('findstr "app.version" "src\main\resources\version.properties" 2^>nul') do (
    set APP_VERSION=%%v
)
echo  Building version: %APP_VERSION%
echo.

:: ── Step 1: Build the fat JAR ────────────────────────────────────────────────
echo  [1/3] Compiling and building JAR (mvn package)...
set PATH=%JAVA_BIN%;%PATH%
"%MVN_CMD%" package -q
if errorlevel 1 (
    echo.
    echo [ERROR] Maven build failed.
    pause & exit /b 1
)
echo        Build successful.
echo.

:: ── Step 2: Create self-contained app-image via jpackage ─────────────────────
echo  [2/3] Packaging into app-image (jpackage)...

if exist "package-input" rd /s /q "package-input"
if exist "dist"          rd /s /q "dist"
if exist "dist-installer" rd /s /q "dist-installer"
mkdir "package-input"
mkdir "dist-installer"
copy /y "target\StockManager-1.0.0.jar" "package-input\" >nul

"%JAVA_BIN%\jpackage.exe" ^
    --type app-image ^
    --input "package-input" ^
    --main-jar "StockManager-1.0.0.jar" ^
    --name "StockManager" ^
    --app-version "%APP_VERSION%" ^
    --java-options "-Djava.net.preferIPv4Stack=true" ^
    --dest "dist"

if errorlevel 1 (
    echo.
    echo [ERROR] jpackage failed.
    pause & exit /b 1
)
rd /s /q "package-input" >nul 2>&1
echo        App-image created: dist\StockManager\StockManager.exe
echo.

:: ── Step 3: Build the Windows installer via Inno Setup ───────────────────────
if "%ISCC%"=="" (
    echo  [3/3] Skipping installer — Inno Setup not found.
    echo        To enable: install Inno Setup from https://jrsoftware.org/isdl.php
) else (
    echo  [3/3] Building Windows installer (Inno Setup)...
    "%ISCC%" /Q "installer.iss"
    if errorlevel 1 (
        echo [ERROR] Inno Setup failed.
        pause & exit /b 1
    )
    echo        Installer created: dist-installer\StockManager-Setup-%APP_VERSION%.exe
)
echo.

echo  ═══════════════════════════════════════════════════════════════
echo   Build complete!
echo  ───────────────────────────────────────────────────────────────
echo   Portable:  dist\StockManager\StockManager.exe
echo   Installer: dist-installer\StockManager-Setup-%APP_VERSION%.exe
echo  ═══════════════════════════════════════════════════════════════
echo.
echo  NEXT STEPS to publish this update to remote PCs:
echo.
echo  1. Go to: https://github.com/Collomut/StoreUpdater/releases/new
echo     Tag:    v%APP_VERSION%
echo     Upload: target\StockManager-1.0.0.jar
echo     Upload: dist-installer\StockManager-Setup-%APP_VERSION%.exe
echo     Click:  Publish release
echo.
echo  2. Update version.json in this folder:
echo       "version": "%APP_VERSION%"
echo       "download_url": "https://github.com/Collomut/StoreUpdater/releases/download/v%APP_VERSION%/StockManager-1.0.0.jar"
echo.
echo  3. Upload version.json to: https://github.com/Collomut/StoreUpdater
echo.
echo  4. Remote PCs auto-update on next launch.
echo  ═══════════════════════════════════════════════════════════════
echo.
pause
