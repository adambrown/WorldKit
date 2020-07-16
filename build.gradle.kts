import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

repositories {
    mavenLocal()
    jcenter()
    maven(url = "https://jitpack.io")
}

plugins {
    java
    kotlin("jvm") version kotlinVersion
    id("org.jetbrains.kotlin.plugin.serialization") version kotlinVersion
    `maven-publish`
}

subprojects {

    apply {
        plugin("java")
        plugin("org.jetbrains.kotlin.jvm")
        plugin("org.jetbrains.kotlin.plugin.serialization")
        plugin("maven-publish")
    }

    tasks {
        withType<KotlinCompile> { kotlinOptions { jvmTarget = "1.8" } }
    }

    group = "worldkit"
    version = "0.1.0"

    repositories {
        mavenLocal()
        jcenter()
        maven(url = "https://jitpack.io")
    }

    val implementation by configurations

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
        implementation(kotlin("reflect"))
        implementation(group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version = kotlinxCoroutinesVersion)
        implementation(group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-runtime", version = kotlinxSerializationRuntimeVersion)
    }

    publishing {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
            }
        }
    }
}