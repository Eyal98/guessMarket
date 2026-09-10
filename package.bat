@echo off
rem ---------------------------------------------------------------------------
rem  Assembles the submission: builds the jars, gathers everything that has to
rem  travel with them, and zips the result.
rem
rem  Everything the program needs is inside one folder, because the commonest
rem  way this fails is a checker unpacking the zip and finding that JavaFX or
rem  engine.jar is somewhere else.
rem
rem  Produces:  dist\guess-market\   the unpacked submission
rem             dist\guess-market.zip
rem ---------------------------------------------------------------------------
setlocal

set ROOT=%~dp0
set DIST=%ROOT%dist
set STAGE=%DIST%\guess-market

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

copy /y "%ROOT%build\guess-market.jar" "%STAGE%\" >nul
copy /y "%ROOT%build\engine.jar" "%STAGE%\" >nul
copy /y "%ROOT%docs\readme.docx" "%STAGE%\readme.docx" >nul

rem All three scripts, because the readme tells the reader to use all three and
rem it would be a poor readme that named files the submission does not carry.
copy /y "%ROOT%run.bat" "%STAGE%\" >nul
copy /y "%ROOT%build.bat" "%STAGE%\" >nul
copy /y "%ROOT%test.bat" "%STAGE%\" >nul

rem JavaFX is not part of the JDK, so it travels with the program or the program
rem does not start on anybody else's computer.
xcopy /e /i /q /y "%ROOT%lib\javafx" "%STAGE%\lib\javafx" >nul

rem The source, laid out exactly as the repository lays it out - so build.bat and
rem test.bat find what they expect and can be run straight from the unpacked zip,
rem rather than being scripts that only work somewhere the reader cannot see.
xcopy /e /i /q /y "%ROOT%engine\src" "%STAGE%\engine\src" >nul
xcopy /e /i /q /y "%ROOT%uifx\src" "%STAGE%\uifx\src" >nul
xcopy /e /i /q /y "%ROOT%uifx\resources" "%STAGE%\uifx\resources" >nul
xcopy /e /i /q /y "%ROOT%uifx\manifest.txt" "%STAGE%\uifx\" >nul
xcopy /e /i /q /y "%ROOT%enginetest\src" "%STAGE%\enginetest\src" >nul
xcopy /e /i /q /y "%ROOT%uifxtest\src" "%STAGE%\uifxtest\src" >nul

rem Every events file the tests read, not only the official ones, so test.bat has
rem what it needs once junit is dropped into tools.
xcopy /e /i /q /y "%ROOT%test-files" "%STAGE%\test-files" >nul

echo Zipping...
powershell -NoProfile -Command "Compress-Archive -Path '%STAGE%' -DestinationPath '%DIST%\guess-market.zip' -Force"
if errorlevel 1 (
    echo.
    echo The zip could not be written.
    exit /b 1
)

echo.
echo Packaged into "%DIST%\guess-market.zip".
echo Unpacked copy is in "%STAGE%".
exit /b 0
