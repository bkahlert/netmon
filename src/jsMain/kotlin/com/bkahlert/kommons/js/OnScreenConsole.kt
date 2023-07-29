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
) {

    private var container: Element? = null

    private fun on(fn: String, args: Array<dynamic>) {
        container?.appendElement("div") {
            setAttribute("data-msg-type", fn)
            appendElement("pre") {
                textContent = formatMessage(args)
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

    companion object {

        /**
         * Formats the message with the given values.
         *
         * Specifiers are handled as follows:
         * - `%s` is formatted as a [String]
         * - `%i` and `%d` are formatted as [Int]
         * - `%f` is formatted as [Float]
         * - `%o` used to be treated as an expandable DOM element, but is treated like `%O`
         * - `%O` is formatted an JavaScript object
         * - `%c` used to be treated as a CSS style declaration but is ignored
         * - `%%` is replaced with a `%`
         */
        private fun formatMessage(args: Array<dynamic>): String {
            val message = args.firstOrNull()?.toString() ?: ""
            val remainingArgs = args.drop(1).toMutableList()
            val messageWithFormattedSubstitutions = message.replace(Regex("%[%idfoOcs]")) { match ->
                when (val specifier = match.value) {
                    "%%" -> "%"
                    else -> {
                        if (remainingArgs.isEmpty()) match.value
                        else formatArg(specifier, remainingArgs.removeFirst())
                    }
                }
            }

            return if (remainingArgs.isEmpty()) {
                messageWithFormattedSubstitutions
            } else {
                val formattedRemainingArgs = remainingArgs.joinToString(" ") { formatArg("%s", it) }
                "$messageWithFormattedSubstitutions $formattedRemainingArgs"
            }
        }

        private fun formatArg(specifier: String, arg: dynamic): String = when (specifier) {
            "%i", "%d", "%f" -> js("Number(arg)").toString()
            "%o", "%O" -> JSON.stringify(arg)
            "%c" -> ""
            else -> {
                val str = js("String(arg)").unsafeCast<String>()
                val isObject = js("typeof arg === 'object'").unsafeCast<Boolean>()
                if (isObject && str == "[object Object]") JSON.stringify(arg)
                else str
            }
        }

        @Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED_ANONYMOUS_PARAMETER")
        private fun Any.tee(vararg fns: String, target: (String, Array<dynamic>) -> Unit) {
            val self = this
            fns.forEach { fn ->
                @Suppress("UnsafeCastFromDynamic")
                js("self[fn] = (function(o) { return function() { target(o.name, Array.from(arguments)); o.apply(this, Array.from(arguments)); }; })(self[fn]);")
            }
        }
    }
}
