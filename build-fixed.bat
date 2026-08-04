@echo off
setlocal
cd /d "%~dp0"

echo [1/3] Stoppe alte Gradle-Daemons ...
call gradlew.bat --stop
if errorlevel 1 goto :fail

echo.
echo [2/3] Pruefe Wrapper-Version. Erwartet: Gradle 9.2.1
call gradlew.bat --version
if errorlevel 1 goto :fail

echo.
echo [3/3] Baue Observable - Remake V1.0 ...
call gradlew.bat clean build --refresh-dependencies
if errorlevel 1 goto :fail

echo.
echo BUILD ERFOLGREICH. Die JAR liegt unter build\libs\
pause
exit /b 0

:fail
echo.
echo BUILD FEHLGESCHLAGEN. Bitte die komplette Ausgabe senden.
pause
exit /b 1
