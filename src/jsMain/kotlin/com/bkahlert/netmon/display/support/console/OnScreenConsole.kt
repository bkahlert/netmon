package com.bkahlert.netmon.display.support.console

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
    private val fadingContainers = mutableSetOf<Element>()
    private val timeouts = mutableSetOf<Int>()
    private var disposed = false

    private fun on(fn: String, args: Array<dynamic>) {
        container?.appendElement("div") {
            setAttribute("data-msg-type", fn)
            appendElement("pre") {
                textContent = formatter.format(args)
            }
        }
    }

    private val subscription = console.observe(listOf("error", "warn", "info", "log", "debug"), ::on)

    private fun scheduleTimeout(delayMillis: Int, action: () -> Unit) {
        val timeout = object { var id: Int = 0 }
        timeout.id = window.setTimeout({
            timeouts.remove(timeout.id)
            action()
        }, delayMillis)
        timeouts += timeout.id
    }

    fun disable() {
        if (disposed) return
        container?.also { old ->
            old.addClass("h-0", "opacity-0")
            fadingContainers += old
            scheduleTimeout(1000) {
                old.remove()
                fadingContainers.remove(old)
            }
        }
        container = null
    }

    fun enable(
        parent: Element = window.document.let { it.body ?: error("$it has no body") }
    ) {
        check(!disposed) { "OnScreenConsole is disposed" }
        disable()
        val current = parent.appendElement("div") {
            className = "onscreen-console transition-all duration-[1s] ease-in-out h-0 opacity-0"
        }
        container = current
        scheduleTimeout(1) {
            if (container === current) current.removeClass("h-0", "opacity-0")
        }
    }

    /** Releases console observation, timers, and all rendered containers. */
    fun dispose() {
        if (disposed) return
        disposed = true
        subscription.dispose()
        timeouts.forEach { window.clearTimeout(it) }
        timeouts.clear()
        container?.remove()
        container = null
        fadingContainers.forEach { it.remove() }
        fadingContainers.clear()
    }

    companion object
}
