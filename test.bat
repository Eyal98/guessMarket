@echo off
rem ---------------------------------------------------------------------------
rem  Developer script. NOT part of the submission.
rem  Compiles the engine and its tests, then runs them with the JUnit 5 console.
rem ---------------------------------------------------------------------------
setlocal enabledelayedexpansion

set ROOT=%~dp0
set JUNIT=%ROOT%tools\junit-platform-console-standalone.jar

if not exist "%JUNIT%" (
    echo Cannot find "%JUNIT%".
    echo Download junit-platform-console-standalone.jar from Maven Central into the tools folder.
    exit /b 1
)

if defined JAVA_HOME (
    set JAVAC="%JAVA_HOME%\bin\javac"
    set JAVA="%JAVA_HOME%\bin\java"
) else (
    set JAVAC=javac
    set JAVA=java
)

echo [1/3] Compiling engine...
if exist "%ROOT%out\enginetest" rmdir /s /q "%ROOT%out\enginetest"
if exist "%ROOT%out\engine" rmdir /s /q "%ROOT%out\engine"
dir /s /b "%ROOT%engine\src\*.java" > "%TEMP%\gm-engine-sources.txt"
%JAVAC% --release 25 -encoding UTF-8 -Xlint:all -d "%ROOT%out\engine" "@%TEMP%\gm-engine-sources.txt"
if errorlevel 1 exit /b 1

echo [2/3] Compiling tests...
dir /s /b "%ROOT%enginetest\src\*.java" > "%TEMP%\gm-test-sources.txt"
%JAVAC% --release 25 -encoding UTF-8 -cp "%ROOT%out\engine;%JUNIT%" -d "%ROOT%out\enginetest" "@%TEMP%\gm-test-sources.txt"
if errorlevel 1 exit /b 1

echo [3/3] Running tests...
%JAVA% -Dgm.testfiles="%ROOT%test-files" -jar "%JUNIT%" execute --class-path "%ROOT%out\engine;%ROOT%out\enginetest" --scan-class-path "%ROOT%out\enginetest" --details=tree --disable-ansi-colors
exit /b %ERRORLEVEL%
