package com.bkahlert.netmon.display.presentation

import com.bkahlert.netmon.display.presentation.uri.Uri
import com.bkahlert.netmon.display.presentation.uri.toUri
import kotlinx.serialization.json.Json

@JsModule("./${DeviceIcons.RESOURCE_NAME}")
@JsNonModule
private external val deviceIconsResourceUriString: String

/** The URL of the resource containing the [DeviceIcons]. */
val DeviceIcons.Companion.resource: Uri get() = deviceIconsResourceUriString.toUri()

/** Creates a [DeviceIcons] instance from the given [resource]. */
suspend fun DeviceIcons.Companion.load(resource: Uri): DeviceIcons = resource.readBytes().let { Json.decodeFromString(it.decodeToString()) }
