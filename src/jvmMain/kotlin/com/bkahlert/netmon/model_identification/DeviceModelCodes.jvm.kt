package com.bkahlert.netmon.model_identification

import kotlinx.serialization.json.Json
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.inputStream

/** The URL of the resource containing the [DeviceModelCodes] mappings. */
val DeviceModelCodes.Companion.resource: URL
    get() = DeviceModelCodes::class.java.classLoader.getResource(RESOURCE_NAME)
        ?: error("Resource $RESOURCE_NAME not found.")

/** Creates a [DeviceModelCodes] instance from the given [resource]. */
fun DeviceModelCodes.Companion.load(resource: URL): DeviceModelCodes =
    resource.openStream().use { Json.decodeFromString(it.readBytes().decodeToString()) }

/** Creates a [DeviceModelCodes] instance from the given [file]. */
fun DeviceModelCodes.Companion.load(file: Path): DeviceModelCodes =
    file.inputStream().buffered().use { Json.decodeFromString(it.readBytes().decodeToString()) }
