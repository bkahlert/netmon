package com.bkahlert.kommons.js

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** A disposable observation of console method calls. */
interface ConsoleSubscription {
    /** Stops this observation; repeated calls do nothing. */
    fun dispose(): Unit
}

/**
 * Observes calls to [levels] on this [Console] and returns a subscription that removes only this [observer].
 *
 * The observer receives the called method name and its arguments before the original console method is invoked.
 */
fun Console.observe(
    levels: List<String>,
    observer: (String, Array<dynamic>) -> Unit,
): ConsoleSubscription {
    val removals = levels.distinct().map { level ->
        observeMethod(level, observer)
    }
    return object : ConsoleSubscription {
        private var disposed = false

        override fun dispose() {
            if (disposed) return
            disposed = true
            removals.forEach { it() }
        }
    }
}

/** Streams calls to [functions] and releases the observation when collection is cancelled. */
fun Console.tee(vararg functions: String): Flow<Pair<String, Array<dynamic>>> = callbackFlow {
    val subscription = observe(functions.toList()) { name, arguments ->
        trySend(name to arguments)
    }
    awaitClose { subscription.dispose() }
}

private class MethodObservation(
    val console: Console,
    val name: String,
) {
    val original: dynamic = console.asDynamic()[name]
    val listeners: dynamic = js("[]")
    val dispatcher: dynamic = createDispatcher(name, original, listeners)
}

private val methodObservations = mutableListOf<MethodObservation>()

private fun Console.observeMethod(
    name: String,
    observer: (String, Array<dynamic>) -> Unit,
): () -> Unit {
    val console = this
    val method = methodObservations.firstOrNull { it.console === console && it.name == name }
        ?: MethodObservation(console, name).also {
            methodObservations += it
            console.asDynamic()[name] = it.dispatcher
        }
    val listener: (String, Array<dynamic>) -> Unit = { calledName, arguments ->
        observer(calledName, arguments)
    }
    method.listeners.push(listener)

    return {
        val index = method.listeners.indexOf(listener) as Int
        if (index >= 0) method.listeners.splice(index, 1)
        if ((method.listeners.length as Int) == 0) {
            if (console.asDynamic()[name] === method.dispatcher) {
                console.asDynamic()[name] = method.original
            }
            methodObservations.remove(method)
        }
    }
}

@Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER")
private fun createDispatcher(
    name: String,
    original: dynamic,
    listeners: dynamic,
): dynamic {
    val state = js("({})")
    state.name = name
    state.original = original
    state.listeners = listeners
    val factory = js(
        "(function(state) { return function() { var args = Array.from(arguments); var result; try { state.listeners.slice().forEach(function(listener) { listener(state.name, args); }); } finally { result = state.original.apply(this, args); } return result; }; })"
    )
    return factory(state)
}
