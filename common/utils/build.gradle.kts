import plugins.ConfigAndroidLibrary
import plugins.ConfigPublish
import utils.artifactIdProperty
import utils.artifactPrefix
import utils.commonPrefix
import utils.versionProperty

plugins {
	id("com.android.library")
	id("kotlin-android")
}

apply<ConfigAndroidLibrary>()
apply<ConfigPublish>()

ext {
    set(artifactIdProperty, "$artifactPrefix$commonPrefix${project.name}")
    set(versionProperty, Configurations.sdkVersionName)
}

android {
	namespace = "com.splunk.android.common.utils"

    defaultConfig {
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    compileOnly(Dependencies.Android.annotation)
	compileOnly(Dependencies.Android.recycler)
	compileOnly(Dependencies.Android.material) // Legacy

    implementation(project(":common:logger"))

    testImplementation(Dependencies.Test.junit)
	testImplementation(Dependencies.Test.robolectric)

    androidTestImplementation(Dependencies.Test.runner)
    androidTestImplementation(Dependencies.Test.junitExt)
}