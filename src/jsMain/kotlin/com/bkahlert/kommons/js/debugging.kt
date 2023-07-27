@file:Suppress("RedundantVisibilityModifier")

package com.bkahlert.kommons.js

import kotlinx.dom.createElement
import org.w3c.dom.Element
import org.w3c.dom.Window

public fun Window.catchErrors() {
    window.onerror = { message, source, lineno, colno, error ->
        console.error("Error: $message\nSource: $source\nLine: $lineno\nColumn: $colno\nError: $error")
        true
    }
}

public fun Console.attachTo(
    container: Element,
    error: Boolean = true,
    warn: Boolean = true,
    info: Boolean = true,
    log: Boolean = true,
    debug: Boolean = false,
) {
    tee(
        *listOfNotNull(
            "error".takeIf { error },
            "warn".takeIf { warn },
            "info".takeIf { info },
            "log".takeIf { log },
            "debug".takeIf { debug },
        ).toTypedArray()
    ) { consoleFn, args ->
        val document = requireNotNull(container.ownerDocument) { "No owner document found for $container" }
        container.prepend(document.createElement("pre") {
            className = "text-fuchsia-500 border-2 m-2 p-2"
            textContent = "${consoleFn.uppercase()}: ${args.joinToString(" ") { it.toString() }}"
        })
    }
}

@Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED_ANONYMOUS_PARAMETER")
private fun Any.tee(vararg fns: String, target: (String, Array<out Any?>) -> Unit) {
    val self = this
    fns.forEach { fn ->
        @Suppress("UnsafeCastFromDynamic")
        js("self[fn] = (function(o) { return function() { target(o.name, Array.from(arguments)); o.apply(this, Array.from(arguments)); }; })(self[fn]);")
    }
}
