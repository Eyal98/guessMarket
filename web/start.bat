@echo off
rem ---------------------------------------------------------------------------
rem  Starts the Guess Market web client and opens it in the browser.
rem
rem    start.bat                            server at http://localhost:8080/guess-market
rem    start.bat <server-address>           another Tomcat
rem    start.bat <server-address> <port>    and the page on another port
rem
rem  The page is served at http://localhost:3000/ by serve.js, which uses
rem  Node.js's own modules only - there is nothing to install. It also passes
rem  the server's API through to Tomcat, which is run exactly as for
rem  exercise 3. Close this window to stop the web client.
rem ---------------------------------------------------------------------------
setlocal

where node >nul 2>&1
if errorlevel 1 (
    echo Node.js was not found. The web client needs Node.js 18 or later on the PATH.
    pause
    exit /b 1
)

set "SERVER=%~1"
if "%SERVER%"=="" set "SERVER=http://localhost:8080/guess-market"
set "PORT=%~2"
if "%PORT%"=="" set "PORT=3000"

node "%~dp0serve.js" "%SERVER%" "%PORT%" --open
if errorlevel 1 pause
exit /b %ERRORLEVEL%
