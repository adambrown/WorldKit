
group = "worldkit"
version = "0.1.0"
description = "WorldKit Example Project"

plugins {
    kotlin("jvm") version "1.4.10"
}

repositories {
    mavenLocal()
    jcenter()
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> { kotlinOptions { jvmTarget = "1.8" } }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("script-runtime"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.9")
    implementation("worldkit:worldkit-api:0.1.0")
}
