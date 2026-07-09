@echo off
title Synapse CLI
echo ========================================
echo   Synapse CLI — Java Agent Engine
echo ========================================
echo.
set JAVA_HOME=E:\Coding_Software\Java\jdk1.8.0_281
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d E:\Coding_Software\Java\Main_Project\synapse-cli
java -jar target\synapse-cli-0.1.0.jar
pause
