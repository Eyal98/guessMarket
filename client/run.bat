@echo off
rem ---------------------------------------------------------------------------
rem  Starts Guess Market.
rem
rem  Keep this file next to guess-market.jar, engine.jar and the lib folder.
rem  Java 25 must be installed and "java" must be on the PATH. JavaFX travels
rem  with the program, so nothing else needs installing.
rem ---------------------------------------------------------------------------
setlocal

rem Work from the folder this file sits in, whatever folder it was started from,
rem so a relative path is looked for next to the program rather than wherever
rem the command prompt happened to be. pushd is used instead of cd so that
rem running from a network share still works.
pushd "%~dp0"

set HERE=%~dp0
set APP=%HERE%guess-market.jar
set FX=%HERE%lib\javafx\lib
if not exist "%APP%" set APP=%HERE%build\guess-market.jar

java -version >nul 2>&1
if errorlevel 1 (
    echo.
    echo Java was not found on this computer.
    echo Please install Java 25 and make sure that "java" can be run from a command prompt,
    echo then start this file again.
    echo.
    popd
    pause
    exit /b 1
)

if not exist "%APP%" (
    echo.
    echo Cannot find guess-market.jar next to this file.
    echo Make sure guess-market.jar and engine.jar are both in "%HERE%".
    echo.
    popd
    pause
    exit /b 1
)

if not exist "%FX%\javafx.controls.jar" (
    echo.
    echo Cannot find the JavaFX libraries at "%FX%".
    echo The lib folder travels with the program and must sit next to guess-market.jar.
    echo Please unpack the whole submission into one folder and try again.
    echo.
    popd
    pause
    exit /b 1
)

java --enable-native-access=javafx.graphics --module-path "%FX%" --add-modules javafx.controls,javafx.fxml -jar "%APP%"
set EXITCODE=%ERRORLEVEL%

if not "%EXITCODE%"=="0" (
    echo.
    echo Guess Market stopped with an error.
    echo If the message above mentions "class file version", the installed Java is older than 25.
    echo If it mentions engine.jar, make sure that file sits next to guess-market.jar.
)

popd
if not "%EXITCODE%"=="0" pause
exit /b %EXITCODE%
