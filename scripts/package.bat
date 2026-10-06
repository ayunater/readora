@echo off
rem Builds Readora installers with jpackage (Windows). Output goes to installers\.
rem
rem   scripts\package.bat            .exe + .msi + portable zip
rem   scripts\package.bat exe        only the .exe installer
rem   scripts\package.bat msi        only the .msi installer
rem   scripts\package.bat portable   only the portable zip
rem
rem Requires: a full JDK 17+ and the WiX Toolset 3.x (https://wixtoolset.org) on PATH for exe/msi.
rem The version comes from src\app\AppInfo.java, so it always matches the code.
setlocal enabledelayedexpansion
cd /d "%~dp0\.."

where jpackage >nul 2>nul || (echo jpackage not found - install a full JDK 17 or newer. & exit /b 1)
set VERSION=
for /f "tokens=2 delims=\"" %%v in ('findstr /c:"VERSION = " src\app\AppInfo.java') do set VERSION=%%v
if "%VERSION%"=="" (echo Could not read VERSION from src\app\AppInfo.java & exit /b 1)
echo Packaging Readora %VERSION%

call build.bat || exit /b 1
rmdir /s /q installers 2>nul
mkdir installers

rem Only the Java modules Readora uses (found with jdeps): ~80 MB runtime instead of ~170 MB.
set COMMON=--name Readora --app-version %VERSION% --vendor "Readora" --description "Readora: The PDF App" --input dist --main-jar Readora.jar --main-class app.Main --icon packaging\logo.ico --add-modules java.base,java.desktop,java.logging,java.naming,java.prefs,java.sql,java.xml,jdk.unsupported,jdk.charsets,jdk.localedata --jlink-options "--strip-debug --no-header-files --no-man-pages --compress zip-6"
rem Never change this UUID: it is how Windows recognises a newer Readora as an upgrade of the installed one.
set WINOPTS=--win-menu --win-shortcut --win-dir-chooser --file-associations packaging\pdf-association-windows.properties --win-upgrade-uuid 3e1ea516-6f8f-4a77-85fa-0077ea8edd5f

set TARGET=%~1
if "%TARGET%"=="" set TARGET=all

if /i "%TARGET%"=="exe"      goto :exe
if /i "%TARGET%"=="msi"      goto :msi
if /i "%TARGET%"=="portable" goto :portable
if /i "%TARGET%"=="all"      goto :exe
echo Unknown target "%TARGET%" (use exe, msi or portable) & exit /b 1

:exe
echo --^> exe
jpackage --type exe %COMMON% %WINOPTS% --dest installers || exit /b 1
if /i "%TARGET%"=="exe" goto :done
:msi
echo --^> msi
jpackage --type msi %COMMON% %WINOPTS% --dest installers || exit /b 1
if /i "%TARGET%"=="msi" goto :done
:portable
echo --^> portable
jpackage --type app-image %COMMON% --dest installers || exit /b 1
powershell -NoProfile -Command "Compress-Archive -Path 'installers\Readora' -DestinationPath 'installers\Readora-%VERSION%-portable-windows.zip' -Force" || exit /b 1
rmdir /s /q installers\Readora

:done
echo.
echo Done. Files in installers\:
dir /b installers
