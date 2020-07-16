
dependencies {
    implementation(project(":worldkit-core"))
    implementation(project(":worldkit-api"))
    implementation(project(":worldkit-ui"))

    implementation("org.lwjgl:lwjgl:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-glfw:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-nanovg:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-nfd:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-opengl:$lwjglVersion")

    runtimeOnly(group = "org.lwjgl", name = "lwjgl", version = lwjglVersion, classifier = lwjglNativeClassifier)
    runtimeOnly(group = "org.lwjgl", name = "lwjgl-glfw", version = lwjglVersion, classifier = lwjglNativeClassifier)
    runtimeOnly(group = "org.lwjgl", name = "lwjgl-nanovg", version = lwjglVersion, classifier = lwjglNativeClassifier)
    runtimeOnly(group = "org.lwjgl", name = "lwjgl-nfd", version = lwjglVersion, classifier = lwjglNativeClassifier)
    runtimeOnly(group = "org.lwjgl", name = "lwjgl-opengl", version = lwjglVersion, classifier = lwjglNativeClassifier)
}