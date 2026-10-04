import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension

plugins {
    kotlin("multiplatform") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    id("com.gradleup.shadow") version "9.6.1"
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
    // Raspberry Pi OS bookworm ships JRE 17, trixie JRE 21; kotest 6 and testcontainers 2 need 11 and 17.
    jvmToolchain(17)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes") // IP and its address types are expect/actual classes
    }

    jvm {
        compilerOptions {
            freeCompilerArgs.add("-Xjsr305=strict")
        }
        binaries {
            executable {
                mainClass.set("com.bkahlert.netmon.Application")
            }
        }
    }

    // viewer component
    js {
        browser {
            commonWebpackConfig {
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).copy(open = false)
            }
        }
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project.dependencies.platform("org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.11.0"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

                implementation(project.dependencies.platform("org.jetbrains.kotlinx:kotlinx-serialization-bom:1.11.0"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))

                implementation(project.dependencies.platform("io.kotest:kotest-bom:6.2.5"))
                implementation("io.kotest:kotest-common")
                implementation("io.kotest:kotest-assertions-core")
                implementation("io.kotest:kotest-assertions-table")

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation("org.slf4j:slf4j-simple:2.0.20") { because("logging to the journal without XML or reflection") }

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j")

                implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5") { because("publish scans over MQTT 3; no transitive dependencies") }
                implementation("org.jmdns:jmdns:3.6.3") { because("mDNS / Bonjour based hostname resolution") }
            }
            languageSettings.optIn("kotlin.io.path.ExperimentalPathApi")
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))

                implementation("io.kotest:kotest-assertions-json")

                implementation(project.dependencies.platform("org.testcontainers:testcontainers-bom:2.0.5"))
                implementation("org.testcontainers:testcontainers")
            }
            languageSettings.optIn("kotlin.io.path.ExperimentalPathApi")
        }

        val jsMain by getting {
            dependencies {
                val fritz2Version = "1.0-RC21"
                implementation("dev.fritz2:core:$fritz2Version")

                implementation(npm("mqtt", "5.16.0")) { because("MQTT client for the browser; imported as mqtt/dist/mqtt.esm") }

                // tailwind
                implementation(npm("tailwindcss", "^3.4")) { because("low-level CSS classes") }

                // webpack
                implementation(devNpm("postcss", "^8.5")) { because("CSS post transformation, e.g. auto-prefixing") }
                implementation(devNpm("postcss-loader", "^8.1")) { because("Loader to process CSS with PostCSS") }
                implementation(devNpm("postcss-import", "^16.1")) { because("@import support") }
                implementation(devNpm("autoprefixer", "^10.4")) { because("auto-prefixing by PostCSS") }
                implementation(devNpm("css-loader", "^7.1"))
                implementation(devNpm("style-loader", "^4.0"))
                implementation(devNpm("cssnano", "^7.0")) { because("CSS minification by PostCSS") }
            }
        }
        val jsTest by getting {
            resources.srcDir("packages/netmon-metrics/src/testdata")
        }
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
            languageSettings.optIn("kotlin.ExperimentalStdlibApi")
            languageSettings.optIn("kotlin.ExperimentalUnsignedTypes")
            languageSettings.optIn("kotlin.io.encoding.ExperimentalEncodingApi")
            languageSettings.optIn("kotlin.time.ExperimentalTime")
            languageSettings.optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
            languageSettings.optIn("kotlinx.coroutines.FlowPreview")
            languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
        }
    }
}

rootProject.plugins.withType<YarnPlugin> {
    rootProject.the<YarnRootExtension>().apply {
        ignoreScriptsProperty.set(false) // suppress "warning Ignored scripts due to flag." warning
        yarnLockMismatchReportProperty.set(YarnLockMismatchReport.NONE)
        reportNewYarnLockProperty.set(true)
        yarnLockAutoReplaceProperty.set(true)
    }
}


tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// `-PunitOnly` leaves out the tests that need Docker (testcontainers), nmap on the host or the internet;
// that is what `make test-jvm` and CI run. A plain `./gradlew jvmTest` still runs everything.
tasks.named<Test>("jvmTest") {
    if (project.hasProperty("unitOnly")) {
        filter {
            excludeTestsMatching("*IntegrationTest")
            excludeTestsMatching("*NmapNetworkScannerTest.scan*")
            // Thread timing under a loaded CI runner; passes locally and failed once in a release run.
            excludeTestsMatching("*SlicedApplicationTest")
        }
    }
}

tasks {
    withType<Jar> {
        archiveVersion.set("")
    }

    // Shadow picks up the jvm target's main compilation and runtime classpath itself.
    named<ShadowJar>("shadowJar") {
        archiveBaseName.set("netmon")
        archiveVersion.set("")
        manifest { attributes["Main-Class"] = "com.bkahlert.netmon.Application" }
        mergeServiceFiles()
    }

    assemble {
        finalizedBy("shadowJar")
    }
}

// The display shows the version it was built from (webpack.config.d/build-version.js). As a task input it keeps a new commit
// from reusing a bundle that names the previous one; webpack asks git itself.
val buildVersion = providers
    .exec { commandLine("git", "describe", "--tags", "--always", "--dirty"); isIgnoreExitValue = true }
    .standardOutput.asText.map { it.trim().ifEmpty { "unknown" } }

tasks.withType<KotlinWebpack>().configureEach {
    inputs.property("buildVersion", buildVersion)
}

// The production bundle and the images and JSON it uses carry a hash of their content in their names (webpack.config.d).
// webpack emits them next to the bundle, so the unhashed copies from the resources are dropped, and index.html, which
// names the unhashed files, is pointed at the hashed ones. The stylesheets are bundled into the bundle,
// and the source map, which the kiosk never loads, stays out of the package.
tasks.named<Sync>("jsBrowserDistribution") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("*.css", "*.map")
    doLast {
        val hashed = Regex("""\.[0-9a-f]{8}\.""")
        File(destinationDir, "images").listFiles().orEmpty().filterNot { hashed.containsMatchIn(it.name) }.forEach { it.delete() }
        File(destinationDir, "assets").listFiles().orEmpty().filterNot { hashed.containsMatchIn(it.name) }.forEach { it.delete() }
        val bundle = destinationDir.listFiles().orEmpty().single { it.name.matches(Regex("""netmon\.[0-9a-f]{8}\.js""")) }
        val loading = File(destinationDir, "images").listFiles().orEmpty().single { it.name.matches(Regex("""loading\.[0-9a-f]{8}\.svg""")) }
        val index = File(destinationDir, "index.html")
        index.writeText(index.readText().replace("netmon.js", bundle.name).replace("images/loading.svg", "images/${loading.name}"))
    }
}
