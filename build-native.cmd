@echo off
setlocal
cd /d "%~dp0"
if exist "build\native\file-picker.exe" exit /b 0
set "PACKETLAB_CXX=C:\msys64\ucrt64\bin\g++.exe"
if not exist "%PACKETLAB_CXX%" (
    echo Native picker compiler not found. PacketLab will use the Java platform dialog.
    exit /b 0
)
if not exist "build\native" mkdir "build\native"
"%PACKETLAB_CXX%" -std=c++17 -O2 -s -static -municode -mwindows "native\windows\file-picker.cpp" -o "build\native\file-picker.exe" -lole32 -lshell32 -luuid
exit /b %errorlevel%