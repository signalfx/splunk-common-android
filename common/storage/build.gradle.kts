import plugins.ConfigAndroidLibrary
import plugins.ConfigPublish
import utils.artifactIdProperty
import utils.artifactPrefix
import utils.commonPrefix
import utils.versionProperty

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-parcelize")
}

ext {
    set(artifactIdProperty, "$artifactPrefix$commonPrefix${project.name}")
    set(versionProperty, Configurations.sdkVersionName)
}

apply<ConfigAndroidLibrary>()
apply<ConfigPublish>()

android {
    namespace = "com.splunk.android.common.storage"
    testNamespace = "com.splunk.android.common.storage.test"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
}

dependencies {
    implementation(project(":common:logger"))
    implementation(project(":common:utils"))

    compileOnly(Dependencies.Android.annotation)

    testImplementation("org.json:json:20180813")
    testImplementation(Dependencies.Test.junit)

    androidTestImplementation(Dependencies.Test.runner)
    androidTestImplementation(Dependencies.Test.junitExt)
}

// Skip during instrumented tests or when explicitly requested
fun isRunningInstrumentedTests(): Boolean {
    val t = gradle.startParameter.taskNames
    return t.any { it.contains("AndroidTest", true) || it.contains("connected", true) || it.contains("managedDevice", true) }
}