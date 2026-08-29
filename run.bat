@echo off
rem ---------------------------------------------------------------------------
rem  Starts Guess Market.
rem
rem  Keep this file next to guess-market.jar and engine.jar. Java 25 or newer
rem  must be installed and "java" must be on the PATH.
rem ---------------------------------------------------------------------------
setlocal

rem Work from the folder this file sits in, whatever folder it was started from. Without this, a
rem relative path typed at the prompt, such as test-files\single.xml, would be looked for wherever
rem the command prompt happened to be rather than next to the program. pushd is used instead of cd
rem so that running from a network share still works.
pushd "%~dp0"

set HERE=%~dp0
set APP=%HERE%guess-market.jar
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

java -jar "%APP%"
set EXITCODE=%ERRORLEVEL%

if not "%EXITCODE%"=="0" (
    echo.
    echo Guess Market stopped with an error.
    echo If the message above mentions "class file version", the installed Java is older than 25.
    echo If it mentions engine.jar, make sure that file sits next to guess-market.jar.
)

popd
echo.
pause
exit /b %EXITCODE%
