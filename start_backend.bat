@echo off
echo Starting Backend... > backend_debug.txt
call mvnw.cmd spring-boot:run >> backend_debug.txt 2>&1
echo Backend Process Exited. >> backend_debug.txt
