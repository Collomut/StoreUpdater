@echo off
cd /d "%~dp0"
where javaw >nul 2>nul
if errorlevel 1 (
    echo Java was not found on this computer. Please install Java 21 or newer and try again.
    pause
    exit /b 1
)
start "" javaw -Djava.net.preferIPv4Stack=true -jar "target\StockManager-1.0.0.jar"