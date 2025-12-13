@echo off
REM script para inicializar o sistema completo Googol no Windows
REM executa Gateway, Barrels, Downloaders e Web Application

echo 🚀 Iniciando sistema Googol...
echo.

echo === Meta 1 - Backend RMI ===
echo.

echo Iniciando Gateway...
start "Googol Gateway" cmd /k mvn exec:java@gateway

timeout /t 3 /nobreak >nul

echo Iniciando Storage Barrel 1...
start "Googol Barrel 1" cmd /k mvn exec:java@barrel -Dexec.arguments=1

timeout /t 2 /nobreak >nul

echo Iniciando Storage Barrel 2...
start "Googol Barrel 2" cmd /k mvn exec:java@barrel -Dexec.arguments=2

timeout /t 2 /nobreak >nul

echo Iniciando Downloader 1...
start "Googol Downloader 1" cmd /k mvn exec:java@downloader -Dexec.arguments=1

timeout /t 2 /nobreak >nul

echo Iniciando Downloader 2...
start "Googol Downloader 2" cmd /k mvn exec:java@downloader -Dexec.arguments=2

echo.
echo === Meta 2 - Web Application ===
echo.

timeout /t 3 /nobreak >nul

echo Iniciando Web Application...
echo A aplicação web estará disponível em: http://localhost:8080
echo.
echo Pressiona Ctrl+C nesta janela para parar a aplicação web
echo (Fecha as outras janelas manualmente para parar os componentes backend)
echo.

mvn spring-boot:run

pause
