package com.bkahlert.netmon

import com.bkahlert.kommons.browser.AutoRefreshers
import com.bkahlert.kommons.js.OnScreenConsole
import kotlinx.datetime.Clock
import com.bkahlert.netmon.model_identification.DeviceModelCodes
import com.bkahlert.netmon.model_identification.load
import com.bkahlert.netmon.model_identification.resource
import kotlin.time.Duration.Companion.seconds
import kotlin.time.times

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

    AutoRefreshers().also { com.bkahlert.kommons.js.console.info("Auto refresh enabled for %s", it.uris) }

    runCatching {
        DeviceModelCodes.set(DeviceModelCodes.load(DeviceModelCodes.resource))
    }.onFailure {
        com.bkahlert.kommons.js.console.error("Device model codes %s failed to load", it)
    }.onSuccess {
        com.bkahlert.kommons.js.console.info("Device model codes %s loaded", it)
    }

    app(
//        scans = flow {
//            emit(scan { index, host ->
//                when (index) {
//                    in 0..3 -> host.copy(status = Status.UP)
//                    else -> host
//                }
//            })
//            delay(5.seconds)
//            emit(scan { index, host ->
//                when (index) {
//                    in 0..3 -> host.copy(status = Status.DOWN)
//                    else -> host
//                }
//            })
//        },
    ) { onScreenConsole.disable() }
}

private fun scan(
    template: (Int) -> Host = {
        Host(
            ip = IP.of("192.168.1.${it + 1}"),
            name = "Host $it",
            status = if (it % 2 == 0) Status.UP else Status.DOWN,
            since = Clock.System.now() - (10 * (it * it).seconds)
        )
    },
    customize: (Int, Host) -> Host = { _, host -> host },
) = EventSource("test", "en0", Cidr.parse("192.168.16.0/24")) to Event.ScanEvent(
    type = Event.ScanEvent.Type.COMPLETED,
    hosts = buildList {
        for (i in 0..100) add(customize(i, template(i)))
    },
    timestamp = Clock.System.now() - 1.seconds,
)
