object Dependencies {

    private const val gradleVersion = "7.3.1"
    private const val kotlinVersion = "1.7.20"
    private const val ktlintVersion = "1.7.1"
    private const val jacocoVersion = "0.8.14"

    const val gradle = "com.android.tools.build:gradle:$gradleVersion"
    const val kotlin = "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion"
    const val ktlint = "com.pinterest.ktlint:ktlint-cli:$ktlintVersion"
    const val jacoco = "org.jacoco:org.jacoco.core:$jacocoVersion"

    object NexusPublish {
        const val id = "io.github.gradle-nexus.publish-plugin"
        const val version = "2.0.0"
    }

    object Test {
        private const val junitVersion = "4.12"
        private const val robolectricVersion = "4.11.1"
        private const val runnerVersion = "1.4.0"
        private const val junitExtVersion = "1.1.3"

        const val junit = "junit:junit:$junitVersion"
        const val junitExt = "androidx.test.ext:junit:$junitExtVersion"
        const val robolectric = "org.robolectric:robolectric:$robolectricVersion"
        const val runner = "androidx.test:runner:$runnerVersion"
    }

    object Android {
        private const val annotationVersion = "1.9.1"
        private const val recyclerVersion = "1.2.1"
        private const val materialVersion = "1.9.0"

        const val annotation = "androidx.annotation:annotation:$annotationVersion"
        const val recycler = "androidx.recyclerview:recyclerview:$recyclerVersion"
        const val material = "com.google.android.material:material:$materialVersion"
    }
}
