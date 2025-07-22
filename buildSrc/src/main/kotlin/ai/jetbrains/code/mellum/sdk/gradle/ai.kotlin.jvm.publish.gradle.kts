import ai.jetbrains.code.mellum.sdk.gradle.publish.maven.configureJvmJarManifest

plugins {
    kotlin("jvm")
    `maven-publish`
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

configureJvmJarManifest("jar")
