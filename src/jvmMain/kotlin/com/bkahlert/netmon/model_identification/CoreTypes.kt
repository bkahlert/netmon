package com.bkahlert.netmon.model_identification

import com.bkahlert.kommons.FileCache
import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.logging.logback.StructuredArguments.v
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries

/**
 * A map containing all [UTExportedTypeDeclaration] instances
 * contained in the specified [bundle],
 * with the optional [transform] applied to each instance.
 */
class CoreTypes(
    /** The bundle the [UTExportedTypeDeclaration] instances are read from. */
    val bundle: Bundle = Bundle(Paths.get(CORE_TYPES_BUNDLE_PATH)),
    /** An optional transformation applied to each instance. */
    val transform: (UTExportedTypeDeclaration) -> UTExportedTypeDeclaration = { it },
) : Map<String, UTExportedTypeDeclaration> by (buildMap {
    bundle.allBundles.forEach { bundle ->
        logger.debug("Processing {}", v(bundle))
        bundle.exportedTypeDeclarations?.map(transform)?.forEach { put(it.identifier, it) }
    }
}) {

    /** Returns a sequence of all types conforming to the given [identifier]. */
    fun conformingTypes(identifier: String): Sequence<UTExportedTypeDeclaration> = sequence {
        val type = get(identifier)
        if (type != null) {
            val queue = type.conformsTo.orEmpty().toMutableSet()
            val conformingTypes = mutableListOf(type)
            yield(type)
            while (queue.isNotEmpty()) {
                val conformingTypeIdentifier = queue.take(1).single()
                    .also { queue.remove(it) }
                val conformingType = checkNotNull(get(conformingTypeIdentifier)) { "Conforming type $conformingTypeIdentifier not found." }
                if (conformingTypes.none { it.identifier == conformingType.identifier }) {
                    conformingTypes.add(conformingType)
                    yield(conformingType)
                    conformingType.conformsTo?.forEach(queue::add)
                }
            }
        }
    }

    /** Returns the description of the most specific conforming type. */
    fun description(identifier: String): String? =
        conformingTypes(identifier).firstNotNullOfOrNull { it.description }

    /** Returns the [Icons] of the most specific conforming type. */
    fun icons(identifier: String): Icons? =
        conformingTypes(identifier).firstNotNullOfOrNull { Icons.of(it).takeUnless(Icons::isEmpty) }

    /**
     * For each type, creates a symlink in the specified [directory], that points to
     * the selected [icon] of the most specific conforming type.
     *
     * The actual icons are stored in the specified [iconCache].
     */
    fun allIcons(
        directory: Path,
        iconCache: FileCache = FileCache(directory.resolve("_cache")),
        icon: (Icons) -> Icon?
    ): Path = directory.apply {
        keys.forEach { identifier ->
            logger.debug("Processing {}", identifier)
            conformingTypes(identifier).windowed(2).firstNotNullOfOrNull { (conformingType, conformedType) ->
                logger.debug("Processing conforming type {}", conformingType.identifier)
                val resolvedIcon = resolve(conformingType.identifier)
                if (!resolvedIcon.exists(LinkOption.NOFOLLOW_LINKS)) {
                    logger.debug("Resolving icon for type {}", conformingType.identifier)
                    Icons.of(conformingType).let(icon)?.let { icon ->
                        kotlin.runCatching {
                            val directoryWithIconResource = iconCache.getOrCollect(icon.name) { icon.toIconSet(this, bundle.allBundles) }
                            val iconResource = directoryWithIconResource.listDirectoryEntries().singleOrNull()
                                ?: error("Icon resource unexpectedly missing in $directoryWithIconResource.")
                            logger.debug("Icon for type resolved: {} -> {}", resolvedIcon, iconResource)
                            resolvedIcon.createSymbolicLinkPointingTo(iconResource)
                        }.onFailure {
                            logger.warn("Icon resource creation failed for {}: {}", v("icon", icon), v("cause", it.message))
                        }.getOrNull()
                    }
                }
                if (resolvedIcon.exists(LinkOption.NOFOLLOW_LINKS)) {
                    logger.debug("Icon exists for type {}", conformingType.identifier)
                    resolvedIcon
                } else {
                    logger.debug("Icon doesn't exist for type {}", conformingType.identifier)
                    resolve(conformedType.identifier).also {
                        logger.debug("Point to conformed type: {} -> {}", resolvedIcon, it)
                        resolvedIcon.createSymbolicLinkPointingTo(it)
                    }.takeIf { it.exists(LinkOption.NOFOLLOW_LINKS) }
                }
            }
        }
    }

    companion object {
        private val logger by SLF4J

        /** Default location to look for macOS's `CoreTypes.bundle`. */
        const val CORE_TYPES_BUNDLE_PATH = "/System/Library/CoreServices/CoreTypes.bundle"
    }
}
