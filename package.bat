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
copy /y "%ROOT%run.bat" "%STAGE%\" >nul
copy /y "%ROOT%docs\readme.docx" "%STAGE%\readme.docx" >nul

rem JavaFX is not part of the JDK, so it travels with the program or the program
rem does not start on anybody else's computer.
xcopy /e /i /q /y "%ROOT%lib\javafx" "%STAGE%\lib\javafx" >nul

rem The source, so the code can be read without cloning anything.
xcopy /e /i /q /y "%ROOT%engine\src" "%STAGE%\src\engine" >nul
xcopy /e /i /q /y "%ROOT%uifx\src" "%STAGE%\src\uifx" >nul
xcopy /e /i /q /y "%ROOT%uifx\resources" "%STAGE%\src\uifx-resources" >nul
xcopy /e /i /q /y "%ROOT%enginetest\src" "%STAGE%\src\enginetest" >nul
xcopy /e /i /q /y "%ROOT%uifxtest\src" "%STAGE%\src\uifxtest" >nul

rem A few events files to try it on.
xcopy /e /i /q /y "%ROOT%test-files\ex2" "%STAGE%\test-files" >nul

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
