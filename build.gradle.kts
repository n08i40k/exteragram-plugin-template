import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val minSdkMajorProperty: Provider<Int> =
    providers.gradleProperty("minSdkMajor").map { it?.toInt() }

val targetSdkMajorProperty: Provider<Int> =
    providers.gradleProperty("targetSdkMajor").map { it?.toInt() }

val targetSdkMinorProperty: Provider<Int> =
    providers.gradleProperty("targetSdkMinor").map { it?.toInt() }

buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    // Android itself.
    id("com.android.library") version "9.0.1"

    // Translations.
    id("de.comahe.i18n4k") version "0.11.2"

    id("io.github.n08i40k.extera")
}

i18n4k {
    packageName = "ru.n08i40k.template.i18n"
    sourceCodeLocales = listOf("en", "ru")
}

android {
    namespace = "ru.n08i40k.template"

    buildFeatures {
        buildConfig = true
    }

    compileSdk {
        version = release(targetSdkMajorProperty.get()) {
            minorApiLevel = targetSdkMinorProperty.get()
        }
    }

    defaultConfig {
        minSdk = minSdkMajorProperty.get()

        lint {
            targetSdk = targetSdkMajorProperty.get()
        }
    }

    buildTypes {
        debug {
            buildConfigField("long", "BUILD_TIME", "0")
        }

        release {
            buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11

        isCoreLibraryDesugaringEnabled = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)

        freeCompilerArgs.add("-Xmetadata-version=2.2.0")
        freeCompilerArgs.add("-Xdont-warn-on-error-suppression")

        optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    // Kotlin.
    implementation(libs.jetbrains.kotlin.stdlib)

    // Coroutines for background tasks.
    implementation(libs.kotlinx.coroutines.core)

    // Translations.
    implementation(libs.i18n4k.core)
    compileOnly(libs.kotlinx.collections.immutable)

    // Hooks.
    compileOnly(libs.aliuhook)

    // Same as below.
    compileOnly(libs.androidx.recyclerview)

    // Do I really need this?
    compileOnly(libs.androidx.lifecycle.viewmodel)

    // Desugar.
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

exteraPlugin {
    pluginId = "exteragram-plugin-template"
    pluginName = "exteraGram plugin template"
    pluginDescription = "A template of exteraGram plugin with embedded DEX"
    pluginAuthor = "@n08i40k_extera"
    pluginVersion = "0.0.0"

    minClientVersion = "12.1.1"

    entryClass = "ru.n08i40k.template.Plugin"

    telegramJar = file("libs/Telegram.jar")
    conflictingPackages = listOf("kotlin")

    proguardFiles = files("proguard-rules.pro")
    minSdk.set(minSdkMajorProperty)

    shadedPackage = "ru.n08i40k.template_shaded"

    // kotlin
    relocate("kotlin", "kotlinx")

    // i18n4k
    relocate("de.comahe.i18n4k")

    relocate("androidx") {
        // An example of excluding package/class from remapping
        // to keep compatibility with host functions, etc.
        exclude("androidx.collection.LongSparseArray")
        exclude("androidx.core.view.inputmethod.InputContentInfoCompat")

        // compileOnly packages are resolved from the host at runtime,
        // so references to them must stay non-relocated.
        exclude("androidx.recyclerview.**")
        exclude("androidx.lifecycle.**")
    }
}