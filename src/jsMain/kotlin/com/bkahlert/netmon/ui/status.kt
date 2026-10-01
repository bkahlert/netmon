package com.bkahlert.netmon.ui

import kotlin.time.Clock
import com.bkahlert.netmon.ConsoleLogStore
import com.bkahlert.netmon.CurrentTimeStore
import dev.fritz2.core.RenderContext
import kotlinx.coroutines.flow.map

fun RenderContext.status(consoleLogStore: ConsoleLogStore) {
    h1("font-bold") { +"Network Monitor" }
    div("opacity-50") {
        +"started "
        span {
            val start = Clock.System.now()
            CurrentTimeStore.data
                .map { now -> (start - now).toMomentString() }
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
