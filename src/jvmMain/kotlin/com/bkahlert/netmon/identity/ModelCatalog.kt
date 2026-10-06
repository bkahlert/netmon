package com.bkahlert.netmon.identity

import com.bkahlert.netmon.Kind
import com.bkahlert.netmon.serialization.JsonFormat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

/**
 * The scanner's recognized model codes and their optional explicit kinds.
 *
 * @property models maps recognized model codes to their kind, or `null` when they remain unclassified.
 */
@Serializable
data class ModelCatalog(
    @SerialName("models") val models: Map<String, Kind?> = emptyMap(),
) : ModelCatalogLookup, Set<String> by models.keys {

    override fun kindOf(modelCode: String): Kind? = models[modelCode]
}

/** A set of recognized model codes with their optional device-kind classifications. */
interface ModelCatalogLookup : Set<String> {
    /** Returns the explicit kind of [modelCode], or `null` if it has no classification. */
    fun kindOf(modelCode: String): Kind?
}

/** Loads the scanner's model catalog from the JVM resources. */
fun loadModelCatalog(): ModelCatalog {
    val resource = ModelCatalog::class.java.classLoader.getResource(RESOURCE_NAME)
        ?: error("Resource $RESOURCE_NAME not found.")
    return resource.openStream().use { JsonFormat.decodeFromString(it.readBytes().decodeToString()) }
}

private const val RESOURCE_NAME = "assets/model-catalog.json"
