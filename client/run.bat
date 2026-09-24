@echo off
rem ---------------------------------------------------------------------------
rem  Starts the Guess Market client.
rem
rem  Keep this file next to guess-market-client.jar, gm-dto.jar and the lib
rem  folder. Java 25 must be installed and "java" must be on the PATH. JavaFX
rem  and every other library travel with the client, so nothing else needs
rem  installing.
rem
rem  The client looks for the server at http://localhost:8080/guess-market,
rem  which is where Tomcat serves guess-market.war. To use a Tomcat on another
rem  port, give the address as the first argument, for example:
rem      run.bat http://localhost:8081/guess-market
rem ---------------------------------------------------------------------------
setlocal

rem Work from the folder this file sits in, whatever folder it was started from.
rem pushd is used instead of cd so that running from a network share still works.
pushd "%~dp0"

set HERE=%~dp0
set APP=%HERE%guess-market-client.jar
set FX=%HERE%lib\javafx\lib

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
    echo Cannot find guess-market-client.jar next to this file.
    echo Make sure the whole client folder was unpacked together.
    echo.
    popd
    pause
    exit /b 1
)

if not exist "%FX%\javafx.controls.jar" (
    echo.
    echo Cannot find the JavaFX libraries at "%FX%".
    echo The lib folder travels with the client and must sit next to guess-market-client.jar.
    echo Please unpack the whole client folder together and try again.
    echo.
    popd
    pause
    exit /b 1
)

java --enable-native-access=javafx.graphics --module-path "%FX%" --add-modules javafx.controls,javafx.fxml -jar "%APP%" %*
set EXITCODE=%ERRORLEVEL%

if not "%EXITCODE%"=="0" (
    echo.
    echo The Guess Market client stopped with an error.
    echo If the message above mentions "class file version", the installed Java is older than 25.
)

popd
if not "%EXITCODE%"=="0" pause
exit /b %EXITCODE%
