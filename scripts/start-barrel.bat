@echo off
REM Script para iniciar Storage Barrel no Windows

if "%1"=="" (
    echo Usage: start-barrel.bat ^<barrel-id^>
    exit /b 1
)

set BARREL_ID=%1

echo Starting Storage Barrel %BARREL_ID%...

REM Compila o código
javac -d ..\bin -cp ..\src ..\src\common\*.java ..\src\barrel\*.java

REM Inicia o barrel
java -cp ..\bin barrel.StorageBarrel %BARREL_ID%
