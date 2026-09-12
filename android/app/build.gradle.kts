plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.harperandroid"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.example.harperandroid"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    lint {
        abortOnError = false
        disable.add("NewApi")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    
    sourceSets {
        getByName("main") {
            jniLibs.srcDir("src/main/jniLibs")
        }
    }
    
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                val releaseDir = file("../../rust/harper-android/target/release").absolutePath
                test.systemProperty("java.library.path", releaseDir)
                test.systemProperty("jna.library.path", releaseDir)
                
                val osName = System.getProperty("os.name").lowercase()
                val libName = when {
                    osName.contains("windows") -> "harper_android.dll"
                    osName.contains("mac") -> "libharper_android.dylib"
                    else -> "libharper_android.so"
                }
                test.systemProperty("uniffi.component.harper_android.libraryOverride", "$releaseDir/$libName")
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    
    // UniFFI dependency
    implementation("net.java.dev.jna:jna:5.14.0@aar")
    
    // For local unit tests on host machine, we need standard desktop JNA
    testImplementation("net.java.dev.jna:jna:5.14.0")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.robolectric:robolectric:4.12.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

tasks.register<Exec>("buildRust") {
    val cargoNdk = if (System.getProperty("os.name").lowercase().contains("windows")) "cargo-ndk.exe" else "cargo-ndk"
    workingDir = file("../../rust/harper-android")
    commandLine("cargo", "ndk", "-t", "arm64-v8a", "-t", "x86_64", "-o", "${project.projectDir}/src/main/jniLibs", "build", "--release")
    
    // cargo-ndk is installed, so we want this to fail if rust build fails
    isIgnoreExitValue = false
}

tasks.whenTaskAdded {
    if (name.startsWith("merge") && name.endsWith("JniLibFolders")) {
        dependsOn("buildRust")
    }
}
