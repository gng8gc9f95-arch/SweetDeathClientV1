@echo off
setlocal
cd /d "%~dp0"
title ChestESP - JAR bauen
echo.
echo  ===============================================
echo   ChestESP Client 2.0 - JAR bauen
echo  ===============================================
echo.

echo "%~dp0" | findstr /i /c:".zip" >nul
if not errorlevel 1 goto inzip
if not exist "src\main\java\com\example\chestesp\ChestEspClient.java" goto nosrc
if not exist "src\main\resources\fabric.mod.json" goto nosrc

where java >nul 2>nul
if errorlevel 1 goto nojava

set "JV="
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JV=%%~v"
set "JMAJOR="
for /f "delims=." %%m in ("%JV%") do set "JMAJOR=%%m"
echo  Gefundene Java-Version: %JV%
if not defined JMAJOR goto nojava
if %JMAJOR% LSS 21 goto oldjava

echo.
echo  Baue die Mod ... beim ersten Mal dauert das 5-10 Minuten,
echo  weil Minecraft und Fabric heruntergeladen werden.
echo.
call gradlew.bat build --no-daemon
if errorlevel 1 goto failed

set "JARSIZE=0"
for %%A in ("build\libs\chestesp-2.0.0.jar") do set "JARSIZE=%%~zA"
if %JARSIZE% LSS 50000 goto emptyjar
copy /y "build\libs\chestesp-2.0.0.jar" "chestesp-2.0.0.jar" >nul
echo.
echo  ===============================================
echo   FERTIG:  %CD%\chestesp-2.0.0.jar  (%JARSIZE% Bytes)
echo  ===============================================
echo   Diese Datei in den mods-Ordner kopieren,
echo   z. B. %APPDATA%\.minecraft\mods
echo   Eine alte chestesp-1.1.0.jar dort vorher loeschen!
echo.
if exist "%APPDATA%\.minecraft\mods\chestesp-1.1.0.jar" echo   [!] Im mods-Ordner liegt noch chestesp-1.1.0.jar - bitte entfernen.
explorer /select,"%CD%\chestesp-2.0.0.jar"
pause
exit /b 0

:nojava
echo.
echo  [!] Java wurde nicht gefunden.
echo      Bitte JDK 21 installieren: https://adoptium.net/temurin/releases/?version=21
echo      Beim Installieren "Set JAVA_HOME variable" und "Add to PATH" anhaken,
echo      danach diese Datei erneut starten.
echo.
pause
exit /b 1

:oldjava
echo.
echo  [!] Java %JMAJOR% ist zu alt - es wird JDK 21 gebraucht.
echo      Download: https://adoptium.net/temurin/releases/?version=21
echo.
pause
exit /b 1

:failed
echo.
echo  [!] Der Build ist fehlgeschlagen.
echo      Bitte die Fehlermeldung oben (ab "What went wrong" bzw. "error:") an Claude schicken.
echo.
pause
exit /b 1
:inzip
echo.
echo  [!] Die Datei wird direkt aus der ZIP gestartet.
echo      Bitte die ZIP zuerst komplett entpacken:
echo      Rechtsklick auf die ZIP -^> "Alle extrahieren..." -^> "Extrahieren",
echo      dann im entpackten Ordner "chestesp" JAR-BAUEN.bat starten.
echo.
pause
exit /b 1

:nosrc
echo.
echo  [!] Der Ordner "src" mit dem Quellcode fehlt hier:
echo      %CD%
echo      Ohne ihn entsteht nur eine leere JAR, die Minecraft ignoriert.
echo      Bitte die ZIP komplett entpacken (Rechtsklick -^> "Alle extrahieren...")
echo      und JAR-BAUEN.bat in dem Ordner starten, in dem auch "src" liegt.
echo.
pause
exit /b 1

:emptyjar
echo.
echo  [!] Die gebaute JAR ist zu klein (%JARSIZE% Bytes) - da fehlt der Mod-Inhalt.
echo      Bitte die ZIP neu und komplett entpacken und es noch einmal versuchen.
echo.
pause
exit /b 1
