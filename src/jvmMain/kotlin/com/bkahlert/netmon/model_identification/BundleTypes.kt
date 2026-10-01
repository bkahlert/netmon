package com.bkahlert.netmon.model_identification

import com.bkahlert.netmon.logging.SLF4J
import net.logstash.logback.argument.StructuredArguments.v
import java.nio.file.Paths

/**
 * The types contained in the specified [bundle],
 * with the optional [transform] applied to each instance.
 */
open class BundleTypes(
    /** The bundle the [UTExportedTypeDeclaration] instances are read from. */
    val bundle: Bundle,
    /** An optional transformation applied to each instance. */
    val transform: (UTExportedTypeDeclaration) -> UTExportedTypeDeclaration = { it },
) : Types(
    declarations = bundle.allBundles.mapNotNull {
        it.exportedTypeDeclarations.also { declarations ->
            if (declarations == null) logger.debug("Skipping {} because it doesn't contain exported type declarations", v("bundle", it))
            else logger.debug("Found {} declarations in {}", v("count", declarations.size), v("bundle", it))
        }
    }.flatMap { it.map(transform) },
    iconResolver = { bundle.allBundles.firstNotNullOfOrNull { bundle -> bundle.iconPath(it) } },
) {

    /** The types contained in the `CoreTypes.bundle` */
    companion object CoreTypes : BundleTypes(
        bundle = Bundle(Paths.get("/System/Library/CoreServices/CoreTypes.bundle")),
        transform = CoreTypesSymbolPatch,
    )
}

private val logger by SLF4J
