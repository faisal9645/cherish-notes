#!/usr/bin/env bash
set -e

echo "========================================================"
echo "  CHERISH NOTES - LIVE RUN & INSTANT DEPLOY (UNIX)"
echo "========================================================"
echo ""

# Ensure .env exists
if [ ! -f .env ] && [ -f .env.example ]; then
    cp .env.example .env
    echo "[OK] Initialized .env from .env.example"
fi

# Ensure debug.keystore exists
if [ ! -f debug.keystore ] && [ -f debug.keystore.base64 ]; then
    base64 -d debug.keystore.base64 > debug.keystore 2>/dev/null || base64 -D debug.keystore.base64 > debug.keystore 2>/dev/null
    echo "[OK] Restored debug.keystore from backup"
fi

chmod +x ./gradlew

# Detect adb
ADB_CMD="adb"
if ! command -v adb &> /dev/null; then
    if [ -f "$HOME/Library/Android/sdk/platform-tools/adb" ]; then
        ADB_CMD="$HOME/Library/Android/sdk/platform-tools/adb"
    elif [ -f "$ANDROID_HOME/platform-tools/adb" ]; then
        ADB_CMD="$ANDROID_HOME/platform-tools/adb"
    fi
fi

while true; do
    echo "[*] Checking for connected devices or emulators..."
    if ! "$ADB_CMD" devices | grep -E "[0-9a-zA-Z].*device$" > /dev/null; then
        echo "[!] No active device or emulator detected. Waiting 5s..."
        sleep 5
        continue
    fi

    echo "[*] Building and installing debug APK..."
    if ./gradlew installDebug --quiet; then
        echo "[OK] Successfully installed!"
        echo "[*] Launching Cherish Notes on device..."
        "$ADB_CMD" shell am start -n com.cherish.notes/com.example.MainActivity > /dev/null 2>&1
        echo "========================================================"
        echo "  APP IS NOW RUNNING LIVE ON YOUR DEVICE!"
        echo "========================================================"
    else
        echo "[X] Build or install failed! Check errors above."
    fi

    echo ""
    read -p "Press [ENTER] to hot-rebuild and push your latest code changes (Ctrl+C to quit)..."
done
