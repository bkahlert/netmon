package com.bkahlert.netmon

import com.bkahlert.kommons.js.OnScreenConsole
import com.bkahlert.netmon.model_identification.DeviceModelCodes
import com.bkahlert.netmon.model_identification.load
import com.bkahlert.netmon.model_identification.resource

@JsModule("./images/loading.svg")
@JsNonModule
private external val loadingImage: String

suspend fun main() {
    // Keep a reference to make sure it's part of the release
    loadingImage

    // When running on an embedded device, the console log is practically inaccessible.
    // Therefore, an on-screen console is used for the first log messages to be readable.
    val onScreenConsole = OnScreenConsole(com.bkahlert.kommons.js.console)
        .apply { enable() }
        .also { com.bkahlert.kommons.js.console.info("On-screen console enabled") }

    runCatching {
        DeviceModelCodes.set(DeviceModelCodes.load(DeviceModelCodes.resource))
    }.onFailure {
        com.bkahlert.kommons.js.console.error("Device model codes %s failed to load", it)
    }.onSuccess {
        com.bkahlert.kommons.js.console.info("Device model codes %s loaded", it)
    }

    app { onScreenConsole.disable() }
}
