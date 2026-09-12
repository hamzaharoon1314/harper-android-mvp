# Development Setup

This document outlines the exact development environment required to build and run Harper Android.

## Required Toolchain
1. **Java Development Kit (JDK)**: Minimum Java 17.
2. **Android SDK**: 
   - Compile SDK Version: 36
   - Build-Tools: Associated with SDK 36.
3. **Android NDK**: Specifically, NDK version `28.2.13676358` (installed side-by-side via Android Studio SDK Manager).
4. **Rust**: Standard installation via `rustup`.
5. **cargo-ndk**: `cargo install cargo-ndk`. Used to cross-compile the Rust backend into Android native targets (`.so` files).
6. **UniFFI**: Used to generate the Kotlin bindings over the Rust JNI interface.
7. **Gradle**: Managed via the Gradle Wrapper (`gradle-8.9`).

## Validation Scripts
To guarantee a reproducible environment, we provide validation scripts that check the `PATH` and SDK directories. 

**Windows (PowerShell)**:
```powershell
.\scripts\check-environment.ps1
```

**macOS/Linux (Bash)**:
```bash
./scripts/check-environment.sh
```

### Script Behaviors
- **Missing Requirements**: Throws clear error messages indicating what dependency is missing and how to install it (e.g., "Please install the NDK via SDK Manager").
- **Exit Codes**: Exits with code `1` upon detecting missing binaries. Exits with code `0` on success.
- **Security**: Relies purely on testing local environment variable paths (like `$ANDROID_HOME` or `%LOCALAPPDATA%\Android\Sdk`). Modifies nothing and exposes no secrets.
