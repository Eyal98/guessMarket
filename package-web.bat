@echo off
rem ---------------------------------------------------------------------------
rem  Assembles the exercise 4 submission - the web client - on its own, leaving
rem  the exercise 3 one (package.bat) alone.
rem
rem  Produces:  dist\guess-market-web\      the unpacked submission
rem             dist\guess-market-web.zip
rem
rem  Inside it:
rem    guess-market.war   the exercise 3 server, unchanged
rem    client\            the exercise 3 JavaFX client, unchanged, to upload events
rem                       files with: the web client leaves uploading to it
rem    web\               start.bat, serve.js, relay.js, package.json, public\
rem    readme.docx        the exercise 4 readme (docs\readme-web.docx)
rem    sample-files\      events files for a JavaFX client to upload
rem    source\web\        the web client's source and its tests
rem
rem  Run it after package.bat, never before it: package.bat empties dist.
rem ---------------------------------------------------------------------------
setlocal

set ROOT=%~dp0
set DIST=%ROOT%dist
set STAGE=%DIST%\guess-market-web

call "%ROOT%build.bat"
if errorlevel 1 (
    echo.
    echo The build failed, so nothing was packaged.
    exit /b 1
)

if not exist "%ROOT%docs\readme-web.docx" (
    echo.
    echo Cannot find docs\readme-web.docx. Make it with:
    echo   python docs\make-readme-docx.py ^<identity-number^> web\README.md docs\readme-web.docx
    exit /b 1
)

where node >nul 2>&1
if errorlevel 1 (
    echo.
    echo Node.js is needed to test the web client before packaging it.
    exit /b 1
)
echo.
echo Testing the web client...
pushd "%ROOT%web"
node --test
if errorlevel 1 (
    popd
    echo.
    echo The web client's tests failed, so nothing was packaged.
    exit /b 1
)
popd

echo.
echo Gathering the submission...
if exist "%STAGE%" rmdir /s /q "%STAGE%"
if exist "%DIST%\guess-market-web.zip" del /q "%DIST%\guess-market-web.zip"
mkdir "%STAGE%\web"

copy /y "%ROOT%build\guess-market.war" "%STAGE%\" >nul
xcopy /e /i /q /y "%ROOT%build\client" "%STAGE%\client" >nul
for %%F in (start.bat serve.js relay.js package.json) do copy /y "%ROOT%web\%%F" "%STAGE%\web\" >nul
xcopy /e /i /q /y "%ROOT%web\public" "%STAGE%\web\public" >nul
copy /y "%ROOT%docs\readme-web.docx" "%STAGE%\readme.docx" >nul
xcopy /e /i /q /y "%ROOT%test-files\ex3" "%STAGE%\sample-files" >nul
xcopy /e /i /q /y "%ROOT%web" "%STAGE%\source\web" >nul

rem The JDK's own jar tool writes the zip, as package.bat explains.
echo Zipping...
if defined JAVA_HOME (set JAR="%JAVA_HOME%\bin\jar") else (set JAR=jar)
%JAR% --create --no-manifest --file "%DIST%\guess-market-web.zip" -C "%DIST%" guess-market-web
if errorlevel 1 (
    echo.
    echo The zip could not be written.
    exit /b 1
)

echo.
echo Packaged into "%DIST%\guess-market-web.zip".
echo Unpacked copy is in "%STAGE%".
exit /b 0
