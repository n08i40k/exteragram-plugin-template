import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileTool

val minSdkMajorProperty: Provider<Int> =
    providers.gradleProperty("minSdkMajor").map { it?.toInt() }

val targetSdkMajorProperty: Provider<Int> =
    providers.gradleProperty("targetSdkMajor").map { it?.toInt() }

val targetSdkMinorProperty: Provider<Int> =
    providers.gradleProperty("targetSdkMinor").map { it?.toInt() }

val telegramJarPathProperty: Provider<String> =
    providers.gradleProperty("telegramJarPath")

val dexOutputDirProperty: Provider<Directory> =
    providers.gradleProperty("dexOutputDir")
        .map { layout.projectDirectory.dir(it) }

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
    // Telegram itself.
    compileOnly(files(telegramJarPathProperty.map { path ->
        file(path).also {
            require(it.isFile) {
                "$path is missing, run `just strip-telegram-jar` to generate it from libs/Telegram.jar"
            }
        }
    }))

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

/**
 * Get list of .jar files in provided configuration.
 * artifactView is required for getting .jar files from .aar libraries.
 */
fun classesJarsOf(configurationName: String): Provider<FileCollection> =
    configurations.named(configurationName).map { configuration ->
        configuration.incoming
            .artifactView {
                attributes.attribute(
                    Attribute.of("artifactType", String::class.java),
                    "android-classes-jar"
                )
                lenient(true)
            }
            .files
    }

androidComponents {
    onVariants { variant ->
        val variantName = variant.name
        val variantTitle = variantName.replaceFirstChar { it.uppercase() }

        val runtimeJars = classesJarsOf("${variantName}RuntimeClasspath")
        val compileJars = classesJarsOf("${variantName}CompileClasspath")

        fun buildDir(child: String): Provider<Directory> =
            layout.buildDirectory.dir(child)

        fun buildFile(child: String): Provider<RegularFile> =
            layout.buildDirectory.file(child)

        val shadedJar = tasks.register<ShadowJar>("shade$variantTitle") {
            destinationDirectory.set(buildDir("intermediates/shaded"))
            archiveFileName.set("classes-${variantName}.jar")

            from(
                tasks.named<KotlinCompileTool>("compile${variantTitle}Kotlin")
                    .flatMap { it.destinationDirectory })
            from(
                tasks.named<JavaCompile>("compile${variantTitle}JavaWithJavac")
                    .flatMap { it.destinationDirectory })
            from(runtimeJars.map { jars -> jars.map(::zipTree) })

            /**
             * Simple shorthand function that relocate provided packages into fixed parent.
             */
            fun rel(pkg: String, action: Action<SimpleRelocator> = Action {}) {
                val pkg = pkg.trimEnd('.') + "."
                return relocate(pkg, "ru.n08i40k.template_shaded.$pkg", action)
            }

            rel("kotlin")
            rel("kotlinx")
            rel("de.comahe.i18n4k")

            rel("androidx") {
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

        tasks.register<BuildDexTask>("buildDex$variantTitle") {
            group = "build"

            programJars.from(shadedJar)

            bootClasspathJars.from(sdkComponents.bootClasspath)

            classpathJars.from(
                compileJars.map { it.minus(runtimeJars.get()) },
                telegramJarPathProperty.map(::file)
            )

            proguardFiles.from(file("proguard-rules.pro"))

            minSdk.set(minSdkMajorProperty)

            release.set(variant.buildType == "release")

            mergedClasspathJar.set(buildFile("intermediates/dex-classpath/${variantName}/classpath.jar"))

            // set output dir to dist folder
            outputDir.set(dexOutputDirProperty.map { it.dir(variantName) })
        }
    }
}

tasks.register("buildDex") {
    group = "build"
    dependsOn(tasks.withType<BuildDexTask>())
}
