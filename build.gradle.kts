plugins {
    kotlin("multiplatform") version "1.9.0"
    kotlin("plugin.serialization") version "1.9.0"
}

group = "com.bkahlert"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")

//    val nativeTarget = when {
//        project.findProperty("nativeTarget") == "linuxArm32Hfp" -> linuxArm32Hfp("native") // will be removed in 1.9.20
//        hostOs == "Mac OS X" -> macosX64("native")
//        hostOs == "Linux" -> linuxX64("native")
//        isMingwX64 -> mingwX64("native")
//        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
//    }
    val nativeTarget = linuxArm32Hfp("native")

    println("Building for ${nativeTarget.platformType}")

    nativeTarget.apply {
        binaries {
            executable {
                entryPoint = "main"
            }
        }
    }

    sourceSets {
        val nativeMain by getting {
            dependencies {
                implementation(platform("org.jetbrains.kotlinx:kotlinx-serialization-bom:1.5.1"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
            }
        }
        val nativeTest by getting

        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
            languageSettings.optIn("kotlin.ExperimentalStdlibApi")
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
            languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
        }
    }
}

val linkTask = tasks.named("linkReleaseExecutableNative")

val cpTask = tasks.register<Exec>("copyReleaseExecutableNativeToRemote") {
    dependsOn(linkTask)
    outputs.file(layout.buildDirectory.file("remote-path.txt").also {
        it.get().asFile.createNewFile()
    })
    outputs.upToDateWhen { false }
    val single = linkTask.get().outputs.files.single()
    commandLine(
        "scp",
        "-r",
        single.absolutePath,
        "bkahlert@unicorn-hd.local:/home/bkahlert",
    )
    doLast {
        outputs.files.singleFile.apply {
            writeText(single.name)
            setLastModified(System.currentTimeMillis())
        }
    }
}

tasks.register<Exec>("runReleaseExecutableNativeRemotely") {
    dependsOn(cpTask)
    commandLine = listOf(
        "ssh",
        "bkahlert@unicorn-hd.local",
        "./" + cpTask.get().outputs.files.singleFile.readText() + "/*.kexe",
    )
}
