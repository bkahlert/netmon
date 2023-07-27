import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer
import org.gradle.kotlin.dsl.support.listFilesOrdered
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport
import org.jetbrains.kotlin.gradle.targets.js.yarn.yarn

plugins {
    kotlin("multiplatform") version "1.9.0"
    kotlin("plugin.serialization") version "1.9.0"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.bkahlert.netmon"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    // scanner component
    // not using native, because the target "linuxArm32Hfp" (required for Raspberry Pi Zero)
    // is no longer supported in Kotlin 1.9.20
    jvm()

    // viewer component
    js(IR) {
        browser {
            commonWebpackConfig(Action<KotlinWebpackConfig> {
                devServer = devServer?.copy(open = false)
            })
        }
        yarn.apply {
            ignoreScripts = false // suppress "warning Ignored scripts due to flag." warning
            yarnLockMismatchReport = YarnLockMismatchReport.NONE
            reportNewYarnLock = true // true
            yarnLockAutoReplace = true // true
        }
    }.binaries.executable()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(platform("com.bkahlert.kommons:kommons-bom:2.8.0"))
                implementation("com.bkahlert.kommons:kommons-time")
                implementation("com.bkahlert.kommons:kommons-uri")

                implementation(platform("org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.7.1"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

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

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation("com.bkahlert.kommons:kommons-logging-core")
                implementation("com.bkahlert.kommons:kommons-logging-logback")
                implementation("com.bkahlert.kommons:kommons-exec") { because("CommandLine, ShellScript") }
                implementation("com.hivemq:hivemq-mqtt-client:1.3.0") { because("publish scans") }
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("io.kotest:kotest-assertions-json")
            }
        }

        val jsMain by getting {
            dependencies {
                val fritz2Version = "1.0-RC6"
                implementation("dev.fritz2:core:$fritz2Version")
                implementation("dev.fritz2:headless:$fritz2Version")

                // tailwind
                implementation(npm("tailwindcss", "^3.3.3")) { because("low-level CSS classes") }

                // optional tailwind plugins
                implementation(devNpm("@tailwindcss/typography", "^0.5")) { because("prose classes to format arbitrary text") }
                implementation(devNpm("tailwind-heropatterns", "^0.0.8")) { because("hero-pattern like striped backgrounds") }

                // webpack
                implementation(devNpm("postcss", "^8.4.17")) { because("CSS post transformation, e.g. auto-prefixing") }
                implementation(devNpm("postcss-loader", "^7.0.1")) { because("Loader to process CSS with PostCSS") }
                implementation(devNpm("postcss-import", "^15.1")) { because("@import support") }
                implementation(devNpm("autoprefixer", "10.4.12")) { because("auto-prefixing by PostCSS") }
                implementation(devNpm("css-loader", "6.7.1"))
                implementation(devNpm("style-loader", "3.3.1"))
                implementation(devNpm("cssnano", "5.1.13")) { because("CSS minification by PostCSS") }
            }
        }
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
            languageSettings.optIn("kotlin.ExperimentalStdlibApi")
            languageSettings.optIn("kotlin.ExperimentalUnsignedTypes")
            languageSettings.optIn("kotlin.io.encoding.ExperimentalEncodingApi")
            languageSettings.optIn("kotlin.time.ExperimentalTime")
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


tasks {
    val removalPattern = listOf(
        Regex("\\.(jpe?g|png|gif|svg)\$"),
        Regex("\\.(woff|woff2|eot|ttf|otf)\$"),
        Regex("mqtt(\\.min)?\\.js\$"),
        Regex("\\.(css)\$"),
    )

    val removalFilter: (File) -> Boolean = { file ->
        removalPattern.any { it.containsMatchIn(file.name) }
    }

    val productionBuilds = withType<KotlinWebpack>().matching { it.name.endsWith("ProductionWebpack") }
    val cleanUpProductionBuild by registering(Delete::class) {
        mustRunAfter(productionBuilds)
        doLast {
            productionBuilds
                .flatMap { task -> task.outputs.files.filter { it.isDirectory } }
                .forEach { distDir -> distDir.listFilesOrdered(removalFilter).forEach { it.delete() } }
        }
    }
    productionBuilds.configureEach { finalizedBy(cleanUpProductionBuild) }
}
