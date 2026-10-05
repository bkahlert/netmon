package com.bkahlert.netmon.model_identification

import kotlinx.serialization.json.Json
import java.net.URL

/** The URL of the resource containing the [DeviceIcons]. */
val DeviceIcons.Companion.resource: URL
    get() = DeviceIcons::class.java.classLoader.getResource(RESOURCE_NAME) ?: error("Resource $RESOURCE_NAME not found.")

/** Creates a [DeviceIcons] instance from the given [resource]. */
fun DeviceIcons.Companion.load(resource: URL): DeviceIcons =
    resource.openStream().use { Json.decodeFromString(it.readBytes().decodeToString()) }
