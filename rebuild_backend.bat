@echo off
setlocal
echo ==========================================
echo      MANUAL BACKEND REBUILD SCRIPT
echo ==========================================
echo.
echo 1. Killing existing Java processes...
taskkill /F /IM java.exe /T 2>nul
if %errorlevel% equ 0 (
    echo    - Java processes killed.
) else (
    echo    - No running Java processes found or access denied.
)

echo.
echo 2. Cleaning project...
call mvnw.cmd clean -DskipTests
if %errorlevel% neq 0 (
    echo    ERROR: Maven Clean failed. 
    echo    Trying global 'mvn' command...
    call mvn clean -DskipTests
)

echo.
echo 3. Compiling project...
call mvnw.cmd compile -DskipTests
if %errorlevel% neq 0 (
    echo    ERROR: Maven Compile failed.
    echo    Trying global 'mvn' command...
    call mvn compile -DskipTests
)

echo.
echo 4. Starting Spring Boot Application (Port 8082)...
echo    (This window will stay open to show logs)
echo.
call mvnw.cmd spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=8082"
if %errorlevel% neq 0 (
    echo    ERROR: Failed to start application via wrapper.
    echo    Trying global 'mvn' command...
    call mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=8082"
)

echo.
echo Server stopped or failed. Press any key to exit.
pause
