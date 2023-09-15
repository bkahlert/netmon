package com.bkahlert.netmon.model_identification

import com.bkahlert.kommons.uri.Uri
import com.bkahlert.kommons.uri.toUri
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get

@JsModule("./${DeviceModelCodes.RESOURCE_NAME}")
@JsNonModule
private external val resourceUriString: String

/** The URL of the resource containing the [DeviceModelCodes] mappings. */
val DeviceModelCodes.Companion.resource: Uri get() = resourceUriString.toUri()

/** Creates a [DeviceModelCodes] instance from the given [resource]. */
suspend fun DeviceModelCodes.Companion.load(resource: Uri): DeviceModelCodes = resource.readBytes().let { Json.decodeFromString(it.decodeToString()) }

suspend fun Uri.readBytes(): ByteArray = window.fetch(toString())
    .await()
    .apply { check(ok) { "Failed to fetch ${this@readBytes}: $statusText" } }
    .arrayBuffer()
    .await()
    .let { Int8Array(it) }
    .let { arr -> ByteArray(arr.length) { arr[it] } }
