package com.bkahlert.netmon.model_identification

import ch.qos.logback.classic.Level
import com.bkahlert.kommons.debug.open
import com.bkahlert.kommons.logging.logback.Logback
import com.bkahlert.kommons.text.capitalize
import com.bkahlert.netmon.logging.get
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.should
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectory
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.reflect.KClass
import kotlin.test.BeforeTest
import kotlin.test.Test

class DeviceModelCodesTestJvm {

    @BeforeTest
    fun setUp() {
        Logback["com.bkahlert.kommons.exec"].level = Level.WARN
        Logback["com.bkahlert.netmon.model_identification"].level = Level.DEBUG
    }

    @Test
    fun resource() = runTest {
        DeviceModelCodes.resource.readText() should { resourceContents ->
            resourceContents.shouldNotBeBlank()
            Json.decodeFromString<DeviceModelCodes>(resourceContents).shouldNotBeEmpty()
        }
    }

    @Test
    fun export_to_resources() = runTest {
        if (!BundleTypes.CoreTypes.bundle.path.exists()) return@runTest

        val types = BundleTypes.CoreTypes + CustomTypes + AmazonTypes + SonosTypes

        val outputDir = DeviceModelCodes::class.getBuildDirectory().resolve("tmp").resolve("device-model-codes").apply {
            createParentDirectories()
            if (exists()) deleteRecursively()
            createDirectory()
        }

        DeviceModelCodesExporter.exportTo(
            types = types,
            directory = outputDir,
        )

        val htmlFile = outputDir.resolve("device-model-codes.embedded-assets.html")
        htmlFile.shouldExist()
        htmlFile.open()

        val jsonFile = outputDir.resolve("device-model-codes.embedded-assets.json")
        jsonFile.shouldExist()

        // hacky, but convenient: Dump working device model codes to sources
        DeviceModelCodes::class.getResourcesDirectory("common", "main").also {
            val packagedResourceFile = it.resolve(DeviceModelCodes.RESOURCE_NAME)
            jsonFile.copyTo(packagedResourceFile.createParentDirectories(), overwrite = true).also {
                println("Packaged ${DeviceModelCodes.RESOURCE_NAME} updated")
            }
        }
    }
}

actual suspend fun testDeviceModelCodesFromResource(): DeviceModelCodes =
    DeviceModelCodes.load(
        DeviceModelCodes::class.java.classLoader.getResource(TEST_DEVICE_CODE_MODES_RESOURCE_NAME)
            ?: error("Resource ${DeviceModelCodes.RESOURCE_NAME} not found.")
    )

fun KClass<*>.findResourcesDirectoryOrNull(target: String? = null, config: String? = null): Path? = java.findResourcesDirectoryOrNull(target, config)
fun KClass<*>.getResourcesDirectory(target: String? = null, config: String? = null): Path = java.getResourcesDirectory(target, config)
fun Class<*>.getResourcesDirectory(target: String? = null, config: String? = null): Path = findResourcesDirectoryOrNull(target, config)
    ?: error("Failed to find the resources directory using $this")

fun Class<*>.findResourcesDirectoryOrNull(target: String? = null, config: String? = null): Path? = findClassesDirectoryOrNull()?.let { classesDir ->
    val buildDir = classesDir.root.resolve(classesDir.reduce { acc, segment ->
        if (acc.last().pathString == "build") acc else acc.resolve(segment)
    })
    val (clazzTarget, clazzConfig) = classesDir.toList().map { it.fileName.pathString }.takeLast(2)
    val configurationName = (target ?: clazzTarget) + (config ?: clazzConfig).capitalize()
    val srcDir = buildDir.resolveSibling("src")
    srcDir.resolve(configurationName).takeIf { it.exists() }?.resolve("resources")
}

fun KClass<*>.findBuildDirectoryOrNull(): Path? = java.findBuildDirectoryOrNull()
fun KClass<*>.getBuildDirectory(): Path = java.getBuildDirectory()
fun Class<*>.getBuildDirectory(): Path = findBuildDirectoryOrNull() ?: error("Failed to find the build directory using $this")
fun Class<*>.findBuildDirectoryOrNull(): Path? = findClassesDirectoryOrNull()?.let { classesDir ->
    classesDir.root.resolve(classesDir.reduce { acc, segment ->
        if (acc.last().pathString == "build") acc else acc.resolve(segment)
    })
}


fun KClass<*>.getClassesDirectory(): Path = java.getClassesDirectory()
fun KClass<*>.findClassesDirectoryOrNull(): Path? = java.findClassesDirectoryOrNull()
fun Class<*>.getClassesDirectory(): Path = findClassesDirectoryOrNull() ?: error("Failed to find the classes directory using $this")

/**
 * Returns directory (e.g. `/home/john/dev/project/build/classes/kotlin/jvm/test`)
 * containing the classes the class represented by this Java class belongs to, or `null` if it can't be located.
 */
fun Class<*>.findClassesDirectoryOrNull(): Path? {
    val className = name
    val topLevelClassName = className.substringBefore('$')
    val topLevelClass = Thread.currentThread().contextClassLoader.loadClass(topLevelClassName) ?: error(buildString {
        append("error loading class $topLevelClassName")
        if (className != topLevelClassName) append(" (for $className)")
    })
    val url = topLevelClass.protectionDomain?.codeSource?.location ?: return null
    return Paths.get(url.toURI())
}
