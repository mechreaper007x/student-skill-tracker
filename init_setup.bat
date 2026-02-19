@echo off
setlocal
cd /d "%~dp0"

echo [1/3] Checking for existing frontend artifacts...
if exist frontend (
    if exist frontend\* (
        echo Found frontend directory. Removing...
        rmdir /s /q frontend
    ) else (
        echo Found frontend FILE. Deleting...
        del /f /q frontend
    )
)

echo [2/3] Creating fresh frontend Angular project...
:: Using --directory ./frontend explicitly creates the folder and unpacks project into it
call npx -y @angular/cli@latest new frontend --directory ./frontend --standalone --routing --style=css --skip-git --skip-tests --inline-style=false --inline-template=false

echo [3/3] Setup complete.
endlocal
