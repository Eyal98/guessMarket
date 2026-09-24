@echo off
rem ---------------------------------------------------------------------------
rem  Developer script. NOT part of the submission.
rem  Compiles the engine, the client and their tests, then runs them with the
rem  JUnit 5 console.
rem
rem  The client's tests of its conversation with a real server run only when a
rem  server is named, because they need Tomcat running with the WAR deployed:
rem      set GM_SERVER=http://localhost:8080/guess-market
rem      test.bat
rem  Without it they are reported as skipped.
rem ---------------------------------------------------------------------------
setlocal enabledelayedexpansion

set ROOT=%~dp0
set OUT=%ROOT%out\test
set JUNIT=%ROOT%tools\junit-platform-console-standalone.jar
set FX=%ROOT%lib\javafx\lib
set GSON=%ROOT%lib\gson\gson-2.14.0.jar
set OKHTTP=
for %%J in (okhttp-4.9.1.jar okio-2.8.0.jar kotlin-stdlib-1.4.10.jar kotlin-stdlib-common-1.4.10.jar annotations-13.0.jar) do (
    set "OKHTTP=!OKHTTP!;%ROOT%lib\okhttp\%%J"
)
set JAVAC_FLAGS=--release 25 -encoding UTF-8 -Xlint:all

if not exist "%JUNIT%" (
    echo Cannot find "%JUNIT%".
    echo Download junit-platform-console-standalone.jar from Maven Central into the tools folder.
    exit /b 1
)

if not exist "%FX%\javafx.controls.jar" (
    echo Cannot find the JavaFX SDK at "%FX%".
    echo The tests cover the client as well as the engine, so the lib\javafx folder
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

set SERVER_FLAG=
if defined GM_SERVER set SERVER_FLAG=-Dgm.server=%GM_SERVER%

if exist "%OUT%" rmdir /s /q "%OUT%"

echo [1/4] Compiling the data objects and the engine...
call :listSources "%ROOT%dto\src" "%TEMP%\gm-test-sources.txt"
%JAVAC% %JAVAC_FLAGS% -d "%OUT%\dto" "@%TEMP%\gm-test-sources.txt"
if errorlevel 1 exit /b 1
call :listSources "%ROOT%engine\src" "%TEMP%\gm-test-sources.txt"
%JAVAC% %JAVAC_FLAGS% -cp "%OUT%\dto" -d "%OUT%\engine" "@%TEMP%\gm-test-sources.txt"
if errorlevel 1 exit /b 1

echo [2/4] Compiling the client...
call :listSources "%ROOT%client\src" "%TEMP%\gm-test-sources.txt"
%JAVAC% %JAVAC_FLAGS% --module-path "%FX%" --add-modules javafx.controls,javafx.fxml -cp "%OUT%\dto;%GSON%%OKHTTP%" -d "%OUT%\client" "@%TEMP%\gm-test-sources.txt"
if errorlevel 1 exit /b 1

echo [3/4] Compiling the tests...
call :listSources "%ROOT%enginetest\src" "%TEMP%\gm-test-sources.txt"
%JAVAC% %JAVAC_FLAGS% -cp "%OUT%\dto;%OUT%\engine;%JUNIT%" -d "%OUT%\enginetest" "@%TEMP%\gm-test-sources.txt"
if errorlevel 1 exit /b 1
call :listSources "%ROOT%clienttest\src" "%TEMP%\gm-test-sources.txt"
%JAVAC% %JAVAC_FLAGS% --module-path "%FX%" --add-modules javafx.controls,javafx.fxml -cp "%OUT%\dto;%OUT%\client;%GSON%%OKHTTP%;%JUNIT%" -d "%OUT%\clienttest" "@%TEMP%\gm-test-sources.txt"
if errorlevel 1 exit /b 1

echo [4/4] Running tests...
%JAVA% -Dgm.testfiles="%ROOT%test-files" %SERVER_FLAG% --module-path "%FX%" --add-modules javafx.controls,javafx.fxml -jar "%JUNIT%" execute --class-path "%OUT%\dto;%OUT%\engine;%OUT%\enginetest;%OUT%\client;%OUT%\clienttest;%ROOT%client\resources;%GSON%%OKHTTP%" --scan-class-path "%OUT%\enginetest" --scan-class-path "%OUT%\clienttest" --details=tree --disable-ansi-colors
exit /b %ERRORLEVEL%

:listSources
rem ---------------------------------------------------------------------------
rem  Writes an argument file listing every .java file under %~1.
rem
rem  Each path is quoted and its separators turned into forward slashes. Plain
rem  "dir /s /b" output is neither, and javac splits an unquoted path at the
rem  first space - so building from any folder whose name contains a space
rem  failed with "invalid flag". Forward slashes are used because a backslash
rem  is an escape character inside a quoted argument file entry.
rem ---------------------------------------------------------------------------
dir /s /b "%~1\*.java" > "%TEMP%\gm-raw-sources.txt"
break > "%~2"
for /f "usebackq delims=" %%F in ("%TEMP%\gm-raw-sources.txt") do (
    set "javaFile=%%F"
    echo "!javaFile:\=/!">> "%~2"
)
del "%TEMP%\gm-raw-sources.txt" 2>nul
goto :eof
