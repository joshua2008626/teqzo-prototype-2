@echo off
cd /d "%~dp0"
if exist out-test rmdir /s /q out-test
mkdir out-test
dir /s /b src\*.java test\*.java > sources.txt
javac -encoding UTF-8 -d out-test @sources.txt
del sources.txt
java -cp out-test com.teqzo.testkit.TestRunner out-test
