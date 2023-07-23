import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer

plugins {
    kotlin("multiplatform") version "1.9.0"
    kotlin("plugin.serialization") version "1.9.0"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.bkahlert"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvm() // no native, as native linuxArm32Hfp (required for Raspberry Pi Zero) is no longer supported in Kotlin 1.9.20

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.github.ajalt.mordant:mordant:2.0.1")
                implementation(platform("org.jetbrains.kotlinx:kotlinx-serialization-bom:1.5.1"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(platform("io.kotest:kotest-bom:5.6.2"))
                implementation("io.kotest:kotest-common")
                implementation("io.kotest:kotest-assertions-core")
                implementation("io.kotest:kotest-assertions-json")
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(platform("com.bkahlert.kommons:kommons-bom:2.8.0"))
                implementation("com.bkahlert.kommons:kommons-exec") { because("CommandLine, ShellScript") }
            }
        }

        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
            languageSettings.optIn("kotlin.ExperimentalStdlibApi")
            languageSettings.optIn("kotlin.experimental.ExperimentalNativeApi")
            languageSettings.optIn("kotlin.io.encoding.ExperimentalEncodingApi")
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
            languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
        // verify by passing "--info" to gradle, and
        // look for: [KOTLIN] Kotlin compilation 'jdkHome' argument:
        // see https://kotlinlang.org/docs/gradle-configure-project.html#gradle-java-toolchains-support
    }
}

application {
    mainClass.set("MainKt")
}

tasks {
    withType<Jar> {
        archiveVersion.set("")
    }

    shadowJar {
        archiveVersion.set("")
        configurations = listOf(
            project.configurations["jvmRuntimeClasspath"],
            project.configurations["jvmTestRuntimeClasspath"], // don't know why, but doesn't find "MainKt" otherwise
        )
        mergeServiceFiles()
        transform(Log4j2PluginsCacheFileTransformer::class.java)
    }

    assemble {
        finalizedBy(shadowJar)
    }
}

tasks {
    val sshDefaultDestination = "10.0.0.2"
    val sshDestination = project.findProperty("ssh.destination") as String? ?: run {
        logger.warn("No ssh.destination specified. Using $sshDefaultDestination")
        sshDefaultDestination
    }

    val shadowJarFile = shadowJar.get().outputs
        .files
        .single { it.name.endsWith(".jar") }

    val cpTask = register<Exec>("copyShadowSsh") {
        dependsOn(shadowJar)
        outputs.upToDateWhen { false }
        commandLine = listOf(
            "scp",
            "-q",
            "-r",
            shadowJarFile.absolutePath,
            "$sshDestination:${shadowJarFile.name}",
        )
    }

    register<Exec>("runShadowSsh") {
        dependsOn(cpTask)
        commandLine = listOf(
            "ssh",
            "-q",
            sshDestination,
            "java -jar './${shadowJarFile.name}'",
        )
    }
}
