plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    //noinspection UseTomlInstead
    implementation("com.gradleup.shadow:shadow-gradle-plugin:9.6.1")
    implementation("com.android.tools:r8:9.4.17")
}

configurations.configureEach {
    resolutionStrategy.force("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
}

configurations.compileClasspath {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-metadata-jvm")
}
