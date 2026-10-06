package com.bkahlert.netmon.ui

import com.bkahlert.netmon.BUILD_VERSION
import com.bkahlert.netmon.ConsoleLogStore
import com.bkahlert.netmon.KioskStats
import com.bkahlert.netmon.cpuText
import com.bkahlert.netmon.memoryText
import dev.fritz2.core.RenderContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/** Renders [consoleLogStore], [kioskStats] and [version], refreshing elapsed time from [startedAt] via [clock]. */
fun RenderContext.status(
    consoleLogStore: ConsoleLogStore,
    kioskStats: Flow<KioskStats?>,
    clock: Flow<Instant>,
    startedAt: Instant,
    version: String = BUILD_VERSION,
) {
    h1("font-bold") { +"Network Monitor" }
    pill(null, version, "the version this display was built from")
    div("flex items-baseline gap-1 empty:hidden") {
        kioskStats.render(this) { stats ->
            if (stats != null) {
                stats.webCpu?.let { pill("web", cpuText(it), "CPU of the web process, share of one core over the last ${stats.interval} s") }
                stats.kioskCpu?.let { pill("kiosk", cpuText(it), "CPU of cog and its WebKit processes, share of one core over the last ${stats.interval} s") }
                stats.kioskMemory?.let { pill("kiosk", memoryText(it), "RAM plus swap of the kiosk unit") }
            }
        }
    }
    div("opacity-50") {
        +"started "
        span {
            clock
                .map { now -> (startedAt - now).toMomentString() }
                .renderText(into = this)
        }
    }
    div("flex-1 text-right truncate font-mono") {
        // Always show the last (relevant) log message at the top of the app.
        consoleLogStore.data.render(this) { (fn, message) ->
            span(
                when (fn) {
                    "error" -> "text-red-500"
                    "warn" -> "text-yellow-500 opacity-75"
                    else -> "opacity-50"
                }
            ) { +message }
        }
    }
}

private fun RenderContext.pill(label: String?, value: String, explanation: String) {
    span("rounded-full border border-slate-100/25 px-1.5 leading-none tabular-nums") {
        attr("title", explanation)
        label?.let { span("opacity-50") { +"$it " } }
        +value
    }
}
