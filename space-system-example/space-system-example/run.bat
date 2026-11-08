@echo off
rem Compile and run the lessons. Needs Java 17 or newer.
rem   run.bat       every lesson
rem   run.bat 4     just lesson 4
cd /d "%~dp0"
if exist out rmdir /s /q out
javac -d out src\space\*.java || exit /b 1
java -cp out space.Main %*
