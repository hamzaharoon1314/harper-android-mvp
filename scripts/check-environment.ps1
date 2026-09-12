Write-Host "Checking Harper Android Build Environment..."
Write-Host "------------------------------------------"
$errors = 0

function Check-Command($cmdName) {
    if (!(Get-Command $cmdName -ErrorAction SilentlyContinue)) {
        Write-Host "X [FAIL] $cmdName is not installed or not in PATH." -ForegroundColor Red
        $script:errors++
    } else {
        Write-Host "V [OK] $cmdName is installed." -ForegroundColor Green
    }
}

Check-Command "java"
Check-Command "rustc"
Check-Command "cargo"
Check-Command "cargo-ndk"

$androidHome = $env:ANDROID_HOME
if ([string]::IsNullOrWhiteSpace($androidHome)) {
    $defaultPath = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    if (Test-Path $defaultPath) {
        $androidHome = $defaultPath
    }
}

if ([string]::IsNullOrWhiteSpace($androidHome) -or !(Test-Path $androidHome)) {
    Write-Host "X [FAIL] ANDROID_HOME is not set or directory does not exist." -ForegroundColor Red
    Write-Host "   Please install Android Studio and set the ANDROID_HOME environment variable."
    $errors++
} else {
    Write-Host "V [OK] Android SDK found at: $androidHome" -ForegroundColor Green

    # Check platform-36
    $platform36Path = Join-Path $androidHome "platforms\android-36"
    if (!(Test-Path $platform36Path)) {
        Write-Host "X [FAIL] Android SDK Platform 36 is missing." -ForegroundColor Red
        Write-Host "   Please install it via Android Studio SDK Manager or sdkmanager ""platforms;android-36""."
        $errors++
    } else {
        Write-Host "V [OK] Android SDK Platform 36 found." -ForegroundColor Green
    }

    # Check build-tools
    $buildToolsPath = Join-Path $androidHome "build-tools"
    if (!(Test-Path $buildToolsPath) -or (Get-ChildItem -Path $buildToolsPath | Measure-Object).Count -eq 0) {
        Write-Host "X [FAIL] Android SDK Build-Tools are missing." -ForegroundColor Red
        Write-Host "   Please install build-tools via SDK Manager."
        $errors++
    } else {
        Write-Host "V [OK] Android SDK Build-Tools found." -ForegroundColor Green
    }

    # Check NDK
    $ndkPath = Join-Path $androidHome "ndk"
    if (!(Test-Path $ndkPath) -or (Get-ChildItem -Path $ndkPath | Measure-Object).Count -eq 0) {
        Write-Host "X [FAIL] Android NDK is missing." -ForegroundColor Red
        Write-Host "   Please install the NDK (Side by side) via SDK Manager."
        $errors++
    } else {
        Write-Host "V [OK] Android NDK found." -ForegroundColor Green
    }
}

Write-Host "------------------------------------------"
if ($errors -gt 0) {
    Write-Host "X Environment checks failed with $errors errors." -ForegroundColor Red
    exit 1
} else {
    Write-Host "V All required environment dependencies are met." -ForegroundColor Green
    exit 0
}
