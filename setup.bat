@echo off
echo PRE-CHECK: Starting setup script > setup_log.txt
where node >> setup_log.txt 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Node.js not found >> setup_log.txt
    exit /b 1
)
echo NODE FOUND. Running ng new... >> setup_log.txt
call npx -y @angular/cli new frontend --directory ./frontend --standalone --routing --style=css --skip-git --skip-tests --inline-style=false --inline-template=false >> setup_log.txt 2>&1
echo DONE. >> setup_log.txt
