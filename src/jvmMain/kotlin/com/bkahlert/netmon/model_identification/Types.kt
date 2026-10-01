package com.bkahlert.netmon.model_identification

import com.bkahlert.kommons.FileCache
import com.bkahlert.netmon.logging.SLF4J
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.kv
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries

open class Types(
    declarations: List<UTExportedTypeDeclaration>,
    private val iconResolver: IconResolver = { null },
) : Map<String, UTExportedTypeDeclaration> by (declarations.associateBy { it.identifier }) {

    constructor(
        vararg declarations: UTExportedTypeDeclaration,
        iconResolver: IconResolver = { null },
    ) : this(declarations.asList(), iconResolver)

    /** Returns a sequence of all types conforming to the given [identifier]. */
    fun conformingTypes(identifier: String): Sequence<UTExportedTypeDeclaration> = sequence {
        logger.debug("Getting conforming types for {}", kv("identifier", identifier))
        val type = get(identifier)
        if (type != null) {
            val queue = type.conformsTo.orEmpty().toMutableSet()
            val conformingTypes = mutableListOf(type)
            yield(type)
            while (queue.isNotEmpty()) {
                val conformingTypeIdentifier = queue.take(1).single()
                    .also { queue.remove(it) }
                val conformingType = checkNotNull(get(conformingTypeIdentifier)) { "Conforming type $conformingTypeIdentifier not found in $keys" }
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
            conformingTypes(identifier).windowed(2, partialWindows = true).firstNotNullOfOrNull {
                val conformingType: UTExportedTypeDeclaration = it.first()
                val conformedType: UTExportedTypeDeclaration? = it.getOrNull(1)
                logger.debug("Processing conforming type {}", conformingType.identifier)
                val resolvedIcon = resolve(conformingType.identifier)
                if (!resolvedIcon.exists(LinkOption.NOFOLLOW_LINKS)) {
                    logger.debug("Resolving icon for type {}", conformingType.identifier)
                    Icons.of(conformingType).let(icon)?.let { icon ->
                        kotlin.runCatching {
                            val directoryWithIconResource = iconCache.getOrCollect(icon.name) { icon.toIconSet(this, iconResolver) }
                            val iconResource = directoryWithIconResource.listDirectoryEntries().singleOrNull()
                                ?: error("Icon resource unexpectedly missing in $directoryWithIconResource.")
                            logger.debug("Icon for type resolved: {} -> {}", resolvedIcon, iconResource)
                            resolvedIcon.createSymbolicLinkPointingTo(iconResource)
                        }.onFailure {
                            logger.warn(
                                "Icon resource creation failed for {}: {}",
                                StructuredArguments.v("icon", icon),
                                StructuredArguments.v("cause", it.message)
                            )
                        }.getOrNull()
                    }
                }
                if (resolvedIcon.exists(LinkOption.NOFOLLOW_LINKS)) {
                    logger.debug("Icon exists for type {}", conformingType.identifier)
                    resolvedIcon
                } else {
                    logger.debug("Icon doesn't exist for type {}", conformingType.identifier)
                    if (conformedType != null) {
                        resolve(conformedType.identifier).also { resolvedConformedTypeIcon ->
                            logger.debug("Point to conformed type: {} -> {}", resolvedIcon, resolvedConformedTypeIcon)
                            resolvedIcon.createSymbolicLinkPointingTo(resolvedConformedTypeIcon)
                        }.takeIf { it.exists(LinkOption.NOFOLLOW_LINKS) }
                    } else {
                        null
                    }
                }
            }
        }
    }

    operator fun plus(other: Types): Types = Types(
        declarations = this.values + other.values,
        iconResolver = { other.iconResolver(it) ?: this.iconResolver(it) },
    )

    companion object {
        private val logger by SLF4J
    }
}
