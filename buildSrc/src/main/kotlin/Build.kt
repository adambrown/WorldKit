import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform.getCurrentOperatingSystem

const val kotlinVersion = "1.4.10"
const val kotlinxCoroutinesVersion = "1.3.9"
const val kotlinxSerializationRuntimeVersion = "1.0-M1-1.4.0-rc"

const val lwjglVersion = "3.2.3"
val lwjglNativeClassifier = with(getCurrentOperatingSystem()) {
    when {
        isWindows -> "natives-windows"
        isMacOsX -> "natives-macos"
        else -> "natives-linux"
    }
}

const val jomlVersion = "1.9.25"
const val coltVersion = "1.2.0"
const val glmVersion = "v1.0.1"
