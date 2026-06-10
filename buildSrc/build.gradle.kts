plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.20") // 1.7.20, 1.9.0
    implementation("com.android.tools.build:gradle:7.3.1") // 7.3.1, 8.13.2
}
