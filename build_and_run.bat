@echo off
echo ============================================
echo   Stock Manager - Build Script
echo ============================================

:: Set paths
set JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot
set MAVEN_HOME=C:\tools\apache-maven-3.9.6
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%

echo Java: %JAVA_HOME%
echo Maven: %MAVEN_HOME%

echo.
echo Building project...
cd /d "%~dp0"
call mvn clean package -q

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ============================================
    echo   BUILD SUCCESSFUL!
    echo   Running StockManager...
    echo ============================================
    java -jar target\StockManager-1.0.0.jar
) else (
    echo.
    echo BUILD FAILED. Check output above for errors.
    pause
)
