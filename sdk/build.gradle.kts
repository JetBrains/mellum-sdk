import ai.jetbrains.code.mellum.sdk.gradle.publish.maven.Publishing.publishToMaven

group = rootProject.group
version = rootProject.version

plugins {
    kotlin
    alias(libs.plugins.kotlin.serialization)
}

repositories {
    mavenCentral()
    maven { url = uri("https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public") }
    maven { url = uri("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies") }
}

dependencies {
    implementation(libs.code.features)
    implementation(libs.eclipse.lsp4)

    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.serialization.kotlinx.json)

    testImplementation(kotlin("test"))
    implementation(libs.kotlin.logging)
    testImplementation(libs.slf4j.api)
    testRuntimeOnly(libs.logback.classic)  // Added logback as the logging implementation

    // TestContainers for Ollama tests
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.junit)
}


tasks.withType<Test> {
    useJUnitPlatform {
        excludeTags("ollama")
    }
}

tasks.register<Test>("jvmOllamaTest") {
    description = "Runs tests that require Ollama"
    group = "verification"

    useJUnitPlatform {
        includeTags("ollama")
    }

    testLogging {
        events("passed", "skipped", "failed")
    }
}

publishToMaven()