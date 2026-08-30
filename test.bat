@echo off
rem ---------------------------------------------------------------------------
rem  Developer script. NOT part of the submission.
rem  Compiles the engine and its tests, then runs them with the JUnit 5 console.
rem ---------------------------------------------------------------------------
setlocal enabledelayedexpansion

set ROOT=%~dp0
set JUNIT=%ROOT%tools\junit-platform-console-standalone.jar
set FX=%ROOT%lib\javafx\lib

if not exist "%JUNIT%" (
    echo Cannot find "%JUNIT%".
    echo Download junit-platform-console-standalone.jar from Maven Central into the tools folder.
    exit /b 1
)

if not exist "%FX%\javafx.controls.jar" (
    echo Cannot find the JavaFX SDK at "%FX%".
    echo The tests cover the user interface as well as the engine, so the lib\javafx folder
    echo must sit next to this file. See README.md for which build to fetch.
    exit /b 1
)

if defined JAVA_HOME (
    set JAVAC="%JAVA_HOME%\bin\javac"
    set JAVA="%JAVA_HOME%\bin\java"
) else (
    set JAVAC=javac
    set JAVA=java
)

echo [1/4] Compiling engine...
if exist "%ROOT%out\enginetest" rmdir /s /q "%ROOT%out\enginetest"
if exist "%ROOT%out\uifxtest" rmdir /s /q "%ROOT%out\uifxtest"
if exist "%ROOT%out\uifx" rmdir /s /q "%ROOT%out\uifx"
if exist "%ROOT%out\engine" rmdir /s /q "%ROOT%out\engine"
dir /s /b "%ROOT%engine\src\*.java" > "%TEMP%\gm-engine-sources.txt"
%JAVAC% --release 25 -encoding UTF-8 -Xlint:all -d "%ROOT%out\engine" "@%TEMP%\gm-engine-sources.txt"
if errorlevel 1 exit /b 1

echo [2/4] Compiling the user interface...
dir /s /b "%ROOT%uifx\src\*.java" > "%TEMP%\gm-uifx-sources.txt"
%JAVAC% --release 25 -encoding UTF-8 --module-path "%FX%" --add-modules javafx.controls,javafx.fxml -cp "%ROOT%out\engine" -d "%ROOT%out\uifx" "@%TEMP%\gm-uifx-sources.txt"
if errorlevel 1 exit /b 1

echo [3/4] Compiling tests...
dir /s /b "%ROOT%enginetest\src\*.java" > "%TEMP%\gm-test-sources.txt"
%JAVAC% --release 25 -encoding UTF-8 -cp "%ROOT%out\engine;%JUNIT%" -d "%ROOT%out\enginetest" "@%TEMP%\gm-test-sources.txt"
if errorlevel 1 exit /b 1
dir /s /b "%ROOT%uifxtest\src\*.java" > "%TEMP%\gm-uifxtest-sources.txt"
%JAVAC% --release 25 -encoding UTF-8 --module-path "%FX%" --add-modules javafx.controls,javafx.fxml -cp "%ROOT%out\engine;%ROOT%out\uifx;%JUNIT%" -d "%ROOT%out\uifxtest" "@%TEMP%\gm-uifxtest-sources.txt"
if errorlevel 1 exit /b 1

echo [4/4] Running tests...
%JAVA% -Dgm.testfiles="%ROOT%test-files" --module-path "%FX%" --add-modules javafx.controls,javafx.fxml -jar "%JUNIT%" execute --class-path "%ROOT%out\engine;%ROOT%out\enginetest;%ROOT%out\uifx;%ROOT%out\uifxtest;%ROOT%uifx\resources" --scan-class-path "%ROOT%out\enginetest" --scan-class-path "%ROOT%out\uifxtest" --details=tree --disable-ansi-colors
exit /b %ERRORLEVEL%
