package com.bkahlert.netmon.model_identification

import com.bkahlert.netmon.logging.SLF4J
import net.logstash.logback.argument.StructuredArguments
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.FileVisitResult
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.visitFileTree
import kotlin.properties.PropertyDelegateProvider
import kotlin.reflect.KProperty

/**
 * A representation of the code and resources stored in a bundle directory on disk.
 *
 * @see <a href="https://developer.apple.com/library/archive/documentation/CoreFoundation/Conceptual/CFBundles/BundleTypes/BundleTypes.html">Bundle Programming Guide</a>
 * @see <a href="https://developer.apple.com/documentation/bundleresources/placing_content_in_a_bundle">Placing Content in a Bundle</a>
 */
class Bundle private constructor(
    /** The path to the bundle directory. */
    val path: Path,
    /** The path to the bundle's [Info.plist][PListFile] file. */
    val informationPropertyListFile: PListFile,
) {
    constructor(path: Path) : this(
        path = path,
        informationPropertyListFile = requireNotNull(path.findInformationPropertyListFile()) {
            "Info.plist missing for bundle $path"
        }
    )

    /** A map, constructed from the [informationPropertyListFile]. */
    val info: JsonObject by lazy { informationPropertyListFile.read() }

    private fun <T> cfBundle(transform: (JsonElement) -> T) = PropertyDelegateProvider { thisRef: Bundle, property: KProperty<*> ->
        lazy {
            val propertyName = "CFBundle${property.name.replaceFirstChar { it.uppercase() }}"
            val propertyValue = checkNotNull(thisRef.info[propertyName]) { "Missing $propertyName for bundle ${thisRef.path}" }
            transform(propertyValue)
        }
    }

    private fun <T, R : T> cfBundle(default: R, transform: (JsonElement) -> T) = PropertyDelegateProvider { thisRef: Bundle, property: KProperty<*> ->
        lazy {
            val propertyName = "CFBundle${property.name.replaceFirstChar { it.uppercase() }}"
            val propertyValue = thisRef.info[propertyName]
            propertyValue?.let { transform(it) } ?: default
        }
    }

    /** The type of bundle. */
    val packageType: PackageType by cfBundle(PackageType.BNDL) { PackageType.valueOf(it.jsonPrimitive.content) }

    /** A unique identifier for a bundle. */
    val identifier: String by cfBundle { it.jsonPrimitive.content }

    /** A user-visible short name for the bundle. */
    val name: String? by cfBundle(null) { it.jsonPrimitive.content }

    /** The version of the build that identifies an iteration of the bundle. */
    val version: String? by cfBundle(null) { it.jsonPrimitive.content }

    private val contentsDirectory: Path by lazy { path.resolve("Contents").takeIf { it.exists() } ?: path }

    /** List of this and all contained bundles. */
    val allBundles: List<Bundle> by lazy {
        buildList {
            add(this@Bundle)
            contentsDirectory.resolve("Library").takeIf { it.exists() }?.let { find(it) }?.forEach { addAll(it.allBundles) }
        }
    }

    /** The subdirectory of the bundle containing resources. */
    val resourceDirectory: Path by lazy { contentsDirectory / "Resources" }

    fun iconPath(name: String): Path? = resourceDirectory.resolve(name).takeIf { it.exists() }

    override fun toString(): String = buildString {
        append("Bundle(")
        listOf(::packageType, ::identifier, ::name, ::version, ::path).joinTo(this, ", ") { "${it.name}=${it.get()}" }
        append(")")
    }

    enum class PackageType {
        /** App */
        APPL,

        /** Framework */
        FMWK,

        /** Bundle */
        BNDL
    }

    companion object {
        private val logger by SLF4J

        private fun Path.findInformationPropertyListFile(): PListFile? =
            listOf("Contents/Info.plist", "Info.plist").firstNotNullOfOrNull {
                resolve(it).takeIf { it.exists() }
            }?.let(::PListFile)

        fun find(path: Path): List<Bundle> = buildList {
            path.visitFileTree(followLinks = true) {
                onPreVisitDirectory { directory, _ ->
                    if (directory.extension == "bundle") {
                        val plist = directory.findInformationPropertyListFile()
                        if (plist != null) {
                            kotlin.runCatching { Bundle(directory, plist) }
                                .onSuccess { bundle -> add(bundle).also { logger.debug("Found {}", StructuredArguments.kv("bundle", bundle)) } }
                                .onFailure { logger.warn("Ignoring unreadable {}", StructuredArguments.kv("bundle", directory), it) }
                            FileVisitResult.SKIP_SUBTREE
                        } else {
                            FileVisitResult.CONTINUE
                        }
                    } else {
                        FileVisitResult.CONTINUE
                    }
                }
            }
        }.sortedBy { it.path }
    }
}
