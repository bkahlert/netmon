package com.bkahlert.netmon.display.app

import com.bkahlert.netmon.display.support.console.OnScreenConsole
import com.bkahlert.netmon.display.support.console.console
import com.bkahlert.netmon.display.presentation.DeviceIcons
import com.bkahlert.netmon.display.presentation.DeviceModelCodes
import com.bkahlert.netmon.display.presentation.load
import com.bkahlert.netmon.display.presentation.resource
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

@JsModule("./images/loading.svg")
@JsNonModule
private external val loadingImage: String

/** Loads display assets and mounts the application in the page's status and network targets. */
suspend fun main() {
    // Keep a reference to make sure it's part of the release
    loadingImage

    // When running on an embedded device, the console log is practically inaccessible.
    // Therefore, an on-screen console is used for the first log messages to be readable.
    val onScreenConsole = OnScreenConsole(console)
        .apply { enable() }
        .also { console.info("On-screen console enabled") }

    runCatching {
        DeviceModelCodes.set(DeviceModelCodes.load(DeviceModelCodes.resource))
    }.onFailure {
        console.error("Device model codes %s failed to load", it)
    }.onSuccess {
        console.info("Device model codes %s loaded", it)
    }

    runCatching {
        DeviceIcons.set(DeviceIcons.load(DeviceIcons.resource))
    }.onFailure {
        console.error("Device icons %s failed to load", it)
    }.onSuccess {
        console.info("Device icons loaded")
    }

    val statusTarget = document.querySelector("#root.app .status") as? HTMLElement
        ?: error("Missing status render target")
    val networksTarget = document.querySelector("#root.app .networks") as? HTMLElement
        ?: error("Missing networks render target")
    app(statusTarget, networksTarget) { onScreenConsole.disable() }
}
