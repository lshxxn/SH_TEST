@echo off
rem Windows: double-click this file to start the news chatbot server on port 8080. Close the window to stop.
cd /d "%~dp0"
chcp 65001 > nul
call mvnw.cmd -q spring-boot:run
pause
