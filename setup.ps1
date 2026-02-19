$ErrorActionPreference = "Stop"
Write-Host "Starting Setup..."
if (Test-Path frontend) {
    Write-Host "Cleaning up old frontend..."
    Remove-Item -Recurse -Force frontend
}
Write-Host "Creating directory..."
New-Item -ItemType Directory -Force frontend | Out-Null
Set-Location frontend
Write-Host "Running ng new..."
# Use call operator & for npx
& npx -y @angular/cli@latest new skill-tracker --directory . --standalone --routing --style=css --skip-git --skip-tests --inline-style=$false --inline-template=$false
Write-Host "Setup Completed."
