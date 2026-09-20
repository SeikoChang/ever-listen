plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.seikochang.ever_listen"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "com.seikochang.ever_listen"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    // Release credentials are supplied through Gradle properties or CI
    // secrets. Never fall back to the debug keystore for a release artifact.
    val releaseStoreFile = providers.gradleProperty("releaseStoreFile").orNull
    val releaseStorePassword = providers.gradleProperty("releaseStorePassword").orNull
    val releaseKeyAlias = providers.gradleProperty("releaseKeyAlias").orNull
    val releaseKeyPassword = providers.gradleProperty("releaseKeyPassword").orNull

    signingConfigs {
        if (releaseStoreFile != null && releaseStorePassword != null &&
            releaseKeyAlias != null && releaseKeyPassword != null
        ) {
            create("release") {
                storeFile = file(checkNotNull(releaseStoreFile))
                storePassword = checkNotNull(releaseStorePassword)
                keyAlias = checkNotNull(releaseKeyAlias)
                keyPassword = checkNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            // An unsigned release is preferable to silently shipping a debug-signed artifact.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("net.bytebuddy:byte-buddy:1.14.12")
}

flutter {
    source = "../.."
}

tasks.configureEach {
    if (name == "packageDebugUnitTestForUnitTest") {
        dependsOn("copyFlutterAssetsDebug")
    }
    if (name == "packageReleaseUnitTestForUnitTest") {
        dependsOn("copyFlutterAssetsRelease")
    }
}
