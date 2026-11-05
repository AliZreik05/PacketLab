@echo off
setlocal
cd /d "%~dp0"
set "PACKETLAB_JDK=%JAVA_HOME%"
if exist "%PACKETLAB_JDK%\bin\javac.exe" goto compile
for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk-*") do if exist "%%D\bin\javac.exe" set "PACKETLAB_JDK=%%D"
if exist "%PACKETLAB_JDK%\bin\javac.exe" goto compile
echo PacketLab needs JDK 21 or newer.
echo Install Eclipse Temurin JDK 21 or set JAVA_HOME to your JDK folder.
pause
exit /b 1
:compile
"%PACKETLAB_JDK%\bin\javac.exe" -encoding UTF-8 -d build *.java
if errorlevel 1 (
    echo.
    echo PacketLab could not compile. Check the error above.
    pause
    exit /b 1
)
call "%~dp0build-native.cmd"
if errorlevel 1 (
    echo Native file dialog could not compile.
    pause
    exit /b 1
)
if /i "%~1"=="--check" exit /b 0
start "" "%PACKETLAB_JDK%\bin\javaw.exe" -cp "%~dp0build" PacketLab
exit /b 0