buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath(Dependencies.gradle)
        classpath(Dependencies.kotlin)
        classpath(Dependencies.jacoco)
    }
}

plugins {
    id(Dependencies.NexusPublish.id) version Dependencies.NexusPublish.version
}

allprojects {
    apply<plugins.ConfigKtLint>()

    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }

    afterEvaluate {
        if (!isReleaseBuild()) {
            version = "$version-SNAPSHOT"
        }
    }
}

nexusPublishing {
    packageGroup.set("com.splunk")
    repositories {
        sonatype {
            username.set(System.getenv("SONATYPE_USER"))
            password.set(System.getenv("SONATYPE_KEY"))
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
        }
    }
}

fun Project.isReleaseBuild(): Boolean {
    return findProperty("release") == "true"
}

val excludedModules: List<String> = project.findProperty("excludedModules")
    ?.toString()
    ?.split(",")
    ?.map { it.trim() }
    ?: emptyList()

subprojects {
    if (name in excludedModules) {
        logger.lifecycle(">>> Publishing disabled for module: '$name'")

        tasks.withType<PublishToMavenRepository> {
            enabled = false
        }
        tasks.withType<Sign> {
            enabled = false
        }
    }
}
