
dependencies {
    implementation(project(":worldkit-core"))
    implementation(project(":worldkit-api"))

    implementation("org.joml:joml:$jomlVersion")

    implementation("org.lwjgl:lwjgl:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-glfw:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-nanovg:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-nfd:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-opengl:$lwjglVersion")
}