@echo off
rem ---------------------------------------------------------------------------
rem  Assembles the submission: builds everything, gathers what has to travel,
rem  and zips it.
rem
rem  Produces:  dist\guess-market\      the unpacked submission
rem             dist\guess-market.zip
rem
rem  Inside it:
rem    guess-market.war   the one WAR the exercise asks for, for Tomcat's webapps
rem    client\            the client, with run.bat, every jar it needs and JavaFX
rem    readme.docx
rem    sample-files\      events files to upload, sound and faulty
rem    source\            the code and the build scripts, laid out as in the
rem                       repository; building it needs the lib folder the
rem                       repository describes, which is not repeated here
rem ---------------------------------------------------------------------------
setlocal

set ROOT=%~dp0
set DIST=%ROOT%dist
set STAGE=%DIST%\guess-market
set SOURCE=%STAGE%\source

call "%ROOT%build.bat"
if errorlevel 1 (
    echo.
    echo The build failed, so nothing was packaged.
    exit /b 1
)

if not exist "%ROOT%docs\readme.docx" (
    echo.
    echo Cannot find docs\readme.docx, which the submission has to carry.
    exit /b 1
)

echo.
echo Gathering the submission...
if exist "%DIST%" rmdir /s /q "%DIST%"
mkdir "%STAGE%"

copy /y "%ROOT%build\guess-market.war" "%STAGE%\" >nul
xcopy /e /i /q /y "%ROOT%build\client" "%STAGE%\client" >nul
copy /y "%ROOT%docs\readme.docx" "%STAGE%\readme.docx" >nul
xcopy /e /i /q /y "%ROOT%test-files\ex3" "%STAGE%\sample-files" >nul

for %%M in (dto engine server enginetest clienttest) do (
    xcopy /e /i /q /y "%ROOT%%%M" "%SOURCE%\%%M" >nul
)
xcopy /e /i /q /y "%ROOT%client\src" "%SOURCE%\client\src" >nul
xcopy /e /i /q /y "%ROOT%client\resources" "%SOURCE%\client\resources" >nul
copy /y "%ROOT%client\manifest.txt" "%SOURCE%\client\" >nul
copy /y "%ROOT%client\run.bat" "%SOURCE%\client\" >nul
xcopy /e /i /q /y "%ROOT%test-files" "%SOURCE%\test-files" >nul
copy /y "%ROOT%build.bat" "%SOURCE%\" >nul
copy /y "%ROOT%test.bat" "%SOURCE%\" >nul
rem IntelliJ's module files are of no use outside the IDE.
del /s /q "%SOURCE%\*.iml" >nul 2>&1

rem The JDK's own jar tool writes the zip. PowerShell 5.1's Compress-Archive, used before, writes
rem entry names with backslashes, which the zip format does not allow and which some tools unpack
rem as single files whose names contain backslashes instead of as folders.
echo Zipping...
if defined JAVA_HOME (set JAR="%JAVA_HOME%\bin\jar") else (set JAR=jar)
%JAR% --create --no-manifest --file "%DIST%\guess-market.zip" -C "%DIST%" guess-market
if errorlevel 1 (
    echo.
    echo The zip could not be written.
    exit /b 1
)

echo.
echo Packaged into "%DIST%\guess-market.zip".
echo Unpacked copy is in "%STAGE%".
exit /b 0
