@echo off
cd /d "%~dp0"
if not exist out call build.bat
chcp 65001 >nul
java -Dfile.encoding=UTF-8 -cp "out;lib\*" com.teqzo.Main %*
