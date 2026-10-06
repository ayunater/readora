@echo off
rem Builds Readora with plain javac + jar (no Maven/Gradle needed). Requires JDK 17+.
rem
rem   build.bat          compile and create dist\Readora.jar (+ dist\libs)
rem   build.bat run      build, then launch the app
rem   build.bat test     build, then run the automated tests
rem   build.bat clean    remove build output
setlocal enabledelayedexpansion
cd /d "%~dp0"

if /i "%~1"=="clean" (
  rmdir /s /q out 2>nul
  rmdir /s /q dist 2>nul
  echo Cleaned.
  exit /b 0
)

where javac >nul 2>nul || (echo javac not found - install a JDK 17 or newer. & exit /b 1)

rmdir /s /q out\classes 2>nul
mkdir out\classes
rem javac @argfiles split on spaces, so write each path quoted and with forward slashes
rem (this keeps the build working when the repo lives in a folder whose name has spaces).
if exist out\sources.txt del out\sources.txt
for /r src %%f in (*.java) do (
  set "p=%%f"
  >> out\sources.txt echo "!p:\=/!"
)
javac -encoding UTF-8 -Xlint:-options -cp "libs\*" -d out\classes @out\sources.txt || exit /b 1
xcopy /e /i /y /q resources out\classes >nul

rmdir /s /q dist 2>nul
mkdir dist\libs
copy /y libs\*.jar dist\libs >nul
set CLASSPATH_LINE=
for %%f in (libs\*.jar) do set CLASSPATH_LINE=!CLASSPATH_LINE! libs/%%~nxf
> out\manifest.txt echo Main-Class: app.Main
>> out\manifest.txt echo Class-Path:!CLASSPATH_LINE!
jar --create --file dist\Readora.jar --manifest out\manifest.txt -C out\classes . || exit /b 1
echo Built dist\Readora.jar

if /i "%~1"=="run" (
  java -jar dist\Readora.jar
  exit /b %errorlevel%
)
if /i "%~1"=="test" (
  mkdir out\test-classes 2>nul
  if exist out\test-sources.txt del out\test-sources.txt
  for /r test %%f in (*.java) do (
    set "p=%%f"
    >> out\test-sources.txt echo "!p:\=/!"
  )
  javac -encoding UTF-8 -Xlint:-options -cp "out\classes;libs\*" -d out\test-classes @out\test-sources.txt || exit /b 1
  java -Djava.awt.headless=true -cp "out\classes;out\test-classes;libs\*" viewer.SmokeTest
  exit /b %errorlevel%
)
