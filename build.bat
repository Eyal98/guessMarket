@echo off
rem ---------------------------------------------------------------------------
rem  Builds Guess Market, exercise 3, using the JDK and the jars in lib.
rem
rem    build\guess-market.war   the server. Put it in Tomcat's webapps folder;
rem                             Tomcat serves it at /guess-market.
rem    build\client\            the client, ready to run: run.bat, its jars and
rem                             the JavaFX it needs, all in one folder.
rem
rem  On the way it makes gm-dto.jar (the data both sides exchange) and
rem  engine.jar (the market), which travel inside the WAR; the client carries
rem  gm-dto.jar but never the engine.
rem ---------------------------------------------------------------------------
setlocal enabledelayedexpansion

set ROOT=%~dp0
set OUT=%ROOT%out
set BUILD=%ROOT%build
set CLIENT=%BUILD%\client
set FX=%ROOT%lib\javafx\lib
set GSON=%ROOT%lib\gson\gson-2.14.0.jar
set SERVLET=%ROOT%lib\servlet\jakarta.servlet-api-6.0.0.jar
set OKHTTP_JARS=okhttp-4.9.1.jar okio-2.8.0.jar kotlin-stdlib-1.4.10.jar kotlin-stdlib-common-1.4.10.jar annotations-13.0.jar
set SOURCE_LIST=%TEMP%\guess-market-sources.txt

rem -g and -parameters keep variable and parameter names in the class files, so
rem anyone opening these jars in an IDE sees the names the source actually uses.
set JAVAC_FLAGS=--release 25 -encoding UTF-8 -Xlint:all -g -parameters

if defined JAVA_HOME (
    set JAVAC="%JAVA_HOME%\bin\javac"
    set JAR="%JAVA_HOME%\bin\jar"
) else (
    set JAVAC=javac
    set JAR=jar
)

call :require "%FX%\javafx.controls.jar" "the JavaFX SDK, in lib\javafx"
if errorlevel 1 exit /b 1
call :require "%GSON%" "gson-2.14.0.jar, in lib\gson"
if errorlevel 1 exit /b 1
call :require "%SERVLET%" "jakarta.servlet-api-6.0.0.jar, in lib\servlet"
if errorlevel 1 exit /b 1
set OKHTTP=
for %%J in (%OKHTTP_JARS%) do (
    call :require "%ROOT%lib\okhttp\%%J" "%%J, in lib\okhttp"
    if errorlevel 1 exit /b 1
    set "OKHTTP=!OKHTTP!;%ROOT%lib\okhttp\%%J"
)

echo Cleaning...
if exist "%OUT%" rmdir /s /q "%OUT%"
if exist "%BUILD%" rmdir /s /q "%BUILD%"
mkdir "%BUILD%"

echo Compiling the data objects...
call :listSources "%ROOT%dto\src" "%SOURCE_LIST%"
%JAVAC% %JAVAC_FLAGS% -d "%OUT%\dto" "@%SOURCE_LIST%"
if errorlevel 1 goto failed
%JAR% --create --file "%BUILD%\gm-dto.jar" -C "%OUT%\dto" .
if errorlevel 1 goto failed

echo Compiling the engine...
call :listSources "%ROOT%engine\src" "%SOURCE_LIST%"
%JAVAC% %JAVAC_FLAGS% -cp "%BUILD%\gm-dto.jar" -d "%OUT%\engine" "@%SOURCE_LIST%"
if errorlevel 1 goto failed
%JAR% --create --file "%BUILD%\engine.jar" -C "%OUT%\engine" .
if errorlevel 1 goto failed

rem Two lints are switched off for this module alone. Every servlet inherits
rem Serializable from HttpServlet, and Tomcat never serializes one, so asking
rem each of them for a serialVersionUID is noise. And Gson's own classes carry
rem annotations from a library Gson needs only when Gson itself is built, which
rem the class file lint would otherwise complain is missing.
echo Compiling the server...
call :listSources "%ROOT%server\src" "%SOURCE_LIST%"
%JAVAC% %JAVAC_FLAGS% -Xlint:-serial,-classfile -cp "%BUILD%\gm-dto.jar;%BUILD%\engine.jar;%GSON%;%SERVLET%" -d "%OUT%\server" "@%SOURCE_LIST%"
if errorlevel 1 goto failed

echo Packing guess-market.war...
xcopy /e /i /q /y "%ROOT%server\web" "%OUT%\war" >nul
xcopy /e /i /q /y "%OUT%\server" "%OUT%\war\WEB-INF\classes" >nul
mkdir "%OUT%\war\WEB-INF\lib"
copy /y "%BUILD%\gm-dto.jar" "%OUT%\war\WEB-INF\lib\" >nul
copy /y "%BUILD%\engine.jar" "%OUT%\war\WEB-INF\lib\" >nul
copy /y "%GSON%" "%OUT%\war\WEB-INF\lib\" >nul
%JAR% --create --file "%BUILD%\guess-market.war" -C "%OUT%\war" .
if errorlevel 1 goto failed

echo Compiling the client...
call :listSources "%ROOT%client\src" "%SOURCE_LIST%"
%JAVAC% %JAVAC_FLAGS% --module-path "%FX%" --add-modules javafx.controls,javafx.fxml -cp "%BUILD%\gm-dto.jar;%GSON%%OKHTTP%" -d "%OUT%\client" "@%SOURCE_LIST%"
if errorlevel 1 goto failed

echo Assembling the client folder...
mkdir "%CLIENT%\lib"
%JAR% --create --file "%CLIENT%\guess-market-client.jar" --main-class gm.client.Launcher --manifest "%ROOT%client\manifest.txt" -C "%OUT%\client" . -C "%ROOT%client\resources" .
if errorlevel 1 goto failed
copy /y "%BUILD%\gm-dto.jar" "%CLIENT%\" >nul
copy /y "%ROOT%client\run.bat" "%CLIENT%\" >nul
copy /y "%GSON%" "%CLIENT%\lib\" >nul
for %%J in (%OKHTTP_JARS%) do copy /y "%ROOT%lib\okhttp\%%J" "%CLIENT%\lib\" >nul
rem JavaFX is not part of the JDK, so it travels with the client, or the client
rem does not start on anybody else's computer.
xcopy /e /i /q /y "%ROOT%lib\javafx" "%CLIENT%\lib\javafx" >nul

del "%SOURCE_LIST%" 2>nul
echo.
echo Build finished.
echo   Server: "%BUILD%\guess-market.war"
echo   Client: "%CLIENT%\run.bat"
exit /b 0

:require
rem ---------------------------------------------------------------------------
rem  Stops the build with a plain message when a third-party jar is missing.
rem ---------------------------------------------------------------------------
if exist "%~1" exit /b 0
echo Cannot find %~2.
echo README.md lists every third-party jar the build needs and where to get it.
exit /b 1

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

:failed
del "%SOURCE_LIST%" 2>nul
echo.
echo BUILD FAILED. See the messages above.
exit /b 1
