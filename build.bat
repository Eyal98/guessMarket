@echo off
rem ---------------------------------------------------------------------------
rem  Builds Guess Market into two jars, using the JDK and the bundled JavaFX SDK.
rem
rem    build\engine.jar         the system engine, free of any user interface
rem    build\guess-market.jar   the JavaFX front end, and the one you run
rem
rem  The two must stay in the same folder, next to a lib\javafx folder:
rem  guess-market.jar names engine.jar on its class path, and JavaFX is found
rem  through the module path given in run.bat.
rem ---------------------------------------------------------------------------
setlocal

set ROOT=%~dp0
set OUT=%ROOT%out
set BUILD=%ROOT%build
set FX=%ROOT%lib\javafx\lib
set SOURCE_LIST=%TEMP%\guess-market-sources.txt

rem -g and -parameters keep variable and parameter names in the class files, so
rem anyone opening these jars in an IDE sees the names the source actually uses.
set JAVAC_FLAGS=--release 25 -encoding UTF-8 -Xlint:all -g -parameters

if defined JAVA_HOME (
    set JAVAC="%JAVA_HOME%\bin\javac"
    set JAR="%JAVA_HOME%\bin\jar"
) else (
    set JAVAC=javac
    set JAR=jar
)

if not exist "%FX%\javafx.controls.jar" (
    echo Cannot find the JavaFX SDK at "%FX%".
    echo The lib\javafx folder must sit next to this file.
    exit /b 1
)

echo Cleaning...
if exist "%OUT%" rmdir /s /q "%OUT%"
if exist "%BUILD%" rmdir /s /q "%BUILD%"
mkdir "%BUILD%"

echo Compiling the engine...
dir /s /b "%ROOT%engine\src\*.java" > "%SOURCE_LIST%"
%JAVAC% %JAVAC_FLAGS% -d "%OUT%\engine" "@%SOURCE_LIST%"
if errorlevel 1 goto failed

echo Packing engine.jar...
%JAR% --create --file "%BUILD%\engine.jar" -C "%OUT%\engine" .
if errorlevel 1 goto failed

echo Compiling the JavaFX user interface...
dir /s /b "%ROOT%uifx\src\*.java" > "%SOURCE_LIST%"
%JAVAC% %JAVAC_FLAGS% --module-path "%FX%" --add-modules javafx.controls,javafx.fxml -cp "%BUILD%\engine.jar" -d "%OUT%\uifx" "@%SOURCE_LIST%"
if errorlevel 1 goto failed

echo Packing guess-market.jar...
%JAR% --create --file "%BUILD%\guess-market.jar" --main-class gm.ui.fx.Launcher --manifest "%ROOT%uifx\manifest.txt" -C "%OUT%\uifx" . -C "%ROOT%uifx\resources" .
if errorlevel 1 goto failed

del "%SOURCE_LIST%" 2>nul
echo.
echo Build finished. The jars are in "%BUILD%".
echo Run the program with run.bat.
exit /b 0

:failed
del "%SOURCE_LIST%" 2>nul
echo.
echo BUILD FAILED. See the messages above.
exit /b 1
