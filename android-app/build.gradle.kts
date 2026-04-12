plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    kotlin("kapt")
}

fun propOrEnv(name: String) = providers.gradleProperty(name)
    .orElse(providers.environmentVariable(name))
    .orElse(providers.environmentVariable("ORG_GRADLE_PROJECT_$name"))

val releaseStoreFile = propOrEnv("AGENTSHELL_UPLOAD_STORE_FILE")
val releaseStorePassword = propOrEnv("AGENTSHELL_UPLOAD_STORE_PASSWORD")
val releaseKeyAlias = propOrEnv("AGENTSHELL_UPLOAD_KEY_ALIAS")
val releaseKeyPassword = propOrEnv("AGENTSHELL_UPLOAD_KEY_PASSWORD")

val hasReleaseSigning =
    releaseStoreFile.isPresent &&
        releaseStorePassword.isPresent &&
        releaseKeyAlias.isPresent &&
        releaseKeyPassword.isPresent

android {
    namespace = "com.agentshell.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.agentshell.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile.get())
                storePassword = releaseStorePassword.get()
                keyAlias = releaseKeyAlias.get()
                keyPassword = releaseKeyPassword.get()
            }
        }
    }

    buildTypes {
        getByName("release") {
            // Keep release deterministic across Android/JVM mixed dependencies.
            // R8 minification can be re-enabled after moving non-Android classes
            // out of the APK dependency graph.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/INDEX.LIST",
            "/META-INF/DEPENDENCIES",
            "/META-INF/LICENSE",
            "/META-INF/LICENSE.txt",
            "/META-INF/NOTICE",
            "/META-INF/NOTICE.txt",
            "/META-INF/*.SF",
            "/META-INF/*.DSA",
            "/META-INF/*.RSA",
        )
    }
    sourceSets["main"].jniLibs.srcDirs("src/main/jniLibs")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":agent-core"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-extended:1.6.7")
    implementation("androidx.compose.ui:ui:1.6.7")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.7")
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.7")
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
