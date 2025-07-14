group = "ai.jetbrains.code"
version = "0.0.1"

plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    application
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
    maven { url = uri("https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public") }
    maven { url = uri("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies") }
}

dependencies {
    implementation(libs.codeFeaturesCli)
    implementation(libs.eclipse.lsp4)

    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.serialization.kotlinx.json)

    testImplementation(libs.kotlin.test)
    implementation(libs.kotlin.logging)
    testImplementation(libs.slf4j.api)
    testRuntimeOnly(libs.logback.classic)  // Added logback as the logging implementation

    // TestContainers for Ollama tests
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.junit)
}

distributions {
    create("app") {
        // By some reason, `distributions.main` (aka `tasks.distZip`) does not build anything, so its configuration is repeated manually.
        // See https://github.com/gradle/gradle/blob/master/platforms/jvm/plugins-application/src/main/java/org/gradle/api/plugins/ApplicationPlugin.java
        contents {
            into("lib/") {
                val jar by tasks.getting
                from(jar)
            }
            into("lib/") {
                from(configurations.runtimeClasspath)
            }
            into("bin") {
                val startScripts by tasks.getting
                from(startScripts)
            }
        }
    }
}

val appZipArtifact = artifacts.add("archives", tasks.named<Zip>("appDistZip").map { it.archiveFile }.get()) {
    type = "app"
    builtBy(tasks.named<Zip>("appDistZip"))
}

tasks.withType<Jar> {
    manifest {
        attributes("Implementation-Version" to version)
    }
}

tasks.shadowJar {
    archiveFileName.set("code-mellum-all.jar")
    isZip64 = true
}

tasks.named<JavaExec>("run") {
    systemProperty("CE_VERSION", version)
}

tasks.named<Zip>("appDistZip") {
    archiveFileName.set("code-mellum-lsp-server.zip")
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
