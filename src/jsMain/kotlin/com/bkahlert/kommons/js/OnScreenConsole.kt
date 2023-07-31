package com.bkahlert.kommons.js

import kotlinx.browser.window
import kotlinx.dom.addClass
import kotlinx.dom.appendElement
import kotlinx.dom.removeClass
import org.w3c.dom.Element

/**
 * An on-screen console that displays all messages
 * logged to the specified [console] after [enable] has been called.
 */
class OnScreenConsole(
    private val console: Console,
    private val formatter: ConsoleLogFormatter = DefaultConsoleLogFormatter,
) {

    private var container: Element? = null

    private fun on(fn: String, args: Array<dynamic>) {
        container?.appendElement("div") {
            setAttribute("data-msg-type", fn)
            appendElement("pre") {
                textContent = formatter.format(args)
            }
        }
    }

    init {
        console.tee("error", "warn", "info", "log", "debug", target = ::on)
    }

    fun disable() {
        container?.also { old ->
            old.addClass("h-0", "opacity-0")
            window.setTimeout({ old.remove() }, 1000)
        }
        container = null
    }

    fun enable(
        parent: Element = window.document.let { it.body ?: error("$it has no body") }
    ) {
        disable()
        container = parent.appendElement("div") {
            className = "onscreen-console transition-all duration-[1s] ease-in-out h-0 opacity-0"
        }
        window.setTimeout({ container?.removeClass("h-0", "opacity-0") }, 1)
    }

    companion object
}
