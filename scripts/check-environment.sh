#!/usr/bin/env bash

# Harper Android Environment Check Script

ERRORS=0

echo "Checking Harper Android Build Environment..."
echo "------------------------------------------"

check_command() {
    if ! command -v "$1" &> /dev/null; then
        echo "❌ [FAIL] $1 is not installed or not in PATH."
        ERRORS=$((ERRORS+1))
    else
        echo "✅ [OK] $1 is installed."
    fi
}

check_command "java"
check_command "rustc"
check_command "cargo"
check_command "cargo-ndk"

# Check Android SDK
if [ -z "$ANDROID_HOME" ]; then
    # Try default macOS/Linux paths
    if [ -d "$HOME/Library/Android/sdk" ]; then
        export ANDROID_HOME="$HOME/Library/Android/sdk"
    elif [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
    fi
fi

if [ -z "$ANDROID_HOME" ] || [ ! -d "$ANDROID_HOME" ]; then
    echo "❌ [FAIL] ANDROID_HOME is not set or directory does not exist."
    echo "   Please install Android Studio and set ANDROID_HOME."
    ERRORS=$((ERRORS+1))
else
    echo "✅ [OK] Android SDK found at: $ANDROID_HOME"
    
    # Check platform-36
    if [ ! -d "$ANDROID_HOME/platforms/android-36" ]; then
        echo "❌ [FAIL] Android SDK Platform 36 is missing."
        echo "   Please install it via Android Studio SDK Manager or sdkmanager \"platforms;android-36\"."
        ERRORS=$((ERRORS+1))
    else
        echo "✅ [OK] Android SDK Platform 36 found."
    fi

    # Check build-tools
    if [ ! -d "$ANDROID_HOME/build-tools" ] || [ -z "$(ls -A "$ANDROID_HOME/build-tools" 2>/dev/null)" ]; then
        echo "❌ [FAIL] Android SDK Build-Tools are missing."
        echo "   Please install build-tools via SDK Manager."
        ERRORS=$((ERRORS+1))
    else
        echo "✅ [OK] Android SDK Build-Tools found."
    fi

    # Check NDK
    if [ ! -d "$ANDROID_HOME/ndk" ] || [ -z "$(ls -A "$ANDROID_HOME/ndk" 2>/dev/null)" ]; then
        echo "❌ [FAIL] Android NDK is missing."
        echo "   Please install the NDK (Side by side) via SDK Manager."
        ERRORS=$((ERRORS+1))
    else
        echo "✅ [OK] Android NDK found."
    fi
fi

echo "------------------------------------------"
if [ $ERRORS -gt 0 ]; then
    echo "❌ Environment checks failed with $ERRORS errors."
    exit 1
else
    echo "✅ All required environment dependencies are met."
    exit 0
fi
