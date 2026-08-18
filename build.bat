@echo off
rem ---------------------------------------------------------------------------
rem  Builds Guess Market into two jars, using nothing but the JDK.
rem
rem    build\engine.jar         the system engine
rem    build\guess-market.jar   the console front end, and the one you run
rem
rem  The two must stay in the same folder: guess-market.jar names engine.jar on
rem  its class path so that "java -jar guess-market.jar" finds it.
rem ---------------------------------------------------------------------------
setlocal

set ROOT=%~dp0
set OUT=%ROOT%out
set BUILD=%ROOT%build
set SOURCE_LIST=%TEMP%\guess-market-sources.txt

if defined JAVA_HOME (
    set JAVAC="%JAVA_HOME%\bin\javac"
    set JAR="%JAVA_HOME%\bin\jar"
) else (
    set JAVAC=javac
    set JAR=jar
)

echo Cleaning...
if exist "%OUT%" rmdir /s /q "%OUT%"
if exist "%BUILD%" rmdir /s /q "%BUILD%"
mkdir "%BUILD%"

rem -g and -parameters keep variable and parameter names in the class files. Without them javac
rem throws the names away, so anyone opening these jars in an IDE sees decompiled code full of
rem var1 and arg0 rather than the names the source actually uses.
set JAVAC_FLAGS=--release 25 -encoding UTF-8 -Xlint:all -g -parameters

echo Compiling the engine...
dir /s /b "%ROOT%engine\src\*.java" > "%SOURCE_LIST%"
%JAVAC% %JAVAC_FLAGS% -d "%OUT%\engine" "@%SOURCE_LIST%"
if errorlevel 1 goto failed

echo Packing engine.jar...
%JAR% --create --file "%BUILD%\engine.jar" -C "%OUT%\engine" .
if errorlevel 1 goto failed

echo Compiling the console user interface...
dir /s /b "%ROOT%ui\src\*.java" > "%SOURCE_LIST%"
%JAVAC% %JAVAC_FLAGS% -cp "%BUILD%\engine.jar" -d "%OUT%\ui" "@%SOURCE_LIST%"
if errorlevel 1 goto failed

echo Packing guess-market.jar...
%JAR% --create --file "%BUILD%\guess-market.jar" --main-class gm.ui.console.ConsoleApp --manifest "%ROOT%ui\manifest.txt" -C "%OUT%\ui" .
if errorlevel 1 goto failed

del "%SOURCE_LIST%" 2>nul
echo.
echo Build finished. The jars are in "%BUILD%".
echo Run the program with run.bat, or with: java -jar "%BUILD%\guess-market.jar"
exit /b 0

:failed
del "%SOURCE_LIST%" 2>nul
echo.
echo BUILD FAILED. See the messages above.
exit /b 1
