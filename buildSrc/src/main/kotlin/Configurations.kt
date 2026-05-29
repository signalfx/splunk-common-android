import org.gradle.api.JavaVersion

object Configurations {

    object Android {
        const val compileVersion = 34
        const val minVersion = 16
        const val targetVersion = 31
    }

    object Compilation {
        const val jvmTarget = "1.8"
        val sourceCompatibility = JavaVersion.VERSION_1_8
        val targetCompatibility = JavaVersion.VERSION_1_8
    }

    const val sdkVersionCode = 1
    const val sdkVersionName = "1.1.4"
}
