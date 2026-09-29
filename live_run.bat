@echo off
setlocal enabledelayedexpansion

title Cherish Notes - Live Run & Hot Deploy
color 0B

set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"

echo ========================================================
echo   CHERISH NOTES - LIVE RUN & INSTANT DEPLOY
echo ========================================================
echo.

:check_device
echo [*] Checking for connected devices or emulators...
"%ADB%" devices | findstr /R /C:"[0-9a-zA-Z].*device$" >nul
if %errorlevel% neq 0 (
    echo.
    echo [!] No active Android device or emulator detected.
    echo.
    echo [!] To run live, please do ONE of the following:
    echo     1. Connect your Android phone with a USB cable
    echo        (Make sure 'USB Debugging' is enabled in Settings)
    echo        OR
    echo     2. Launch an Android Virtual Device / Emulator in Android Studio
    echo.
    echo [*] Checking again in 5 seconds... (Press Ctrl+C to cancel)
    timeout /t 5 >nul
    goto check_device
)

echo [OK] Connected device found!
echo.

:deploy_loop
echo [*] Compiling and deploying debug build to device...
call gradlew.bat installDebug --quiet

if %errorlevel% neq 0 (
    echo.
    echo [X] Build or install failed! Check errors above.
) else (
    echo [OK] Successfully installed!
    echo [*] Launching Cherish Notes on device...
    "%ADB%" shell am start -n com.cherish.notes/com.example.MainActivity >nul 2>&1
    echo.
    echo ========================================================
    echo   APP IS NOW RUNNING LIVE ON YOUR DEVICE!
    echo ========================================================
)

echo.
echo Press [ENTER] to hot-rebuild and push your latest code changes...
echo (Or close this window to stop)
pause >nul
echo.
echo [*] Refreshing build with latest changes...
goto deploy_loop
