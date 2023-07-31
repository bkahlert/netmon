package com.bkahlert.kommons.js

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Overrides the specified [functions] of `this` instance,
 * so that for each invoked function,
 * additionally the specified [target]
 * with the name and the arguments of the originally invoked function
 * is called.
 */
@Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED_ANONYMOUS_PARAMETER")
fun Any.tee(vararg functions: String, target: (String, Array<dynamic>) -> Unit) {
    val self = this
    functions.forEach { fn ->
        @Suppress("UnsafeCastFromDynamic")
        js("self[fn] = (function(o,n) { return function() { target(n, Array.from(arguments)); o.apply(this, Array.from(arguments)); }; })(self[fn],fn);")
    }
}

/**
 * Overrides the specified [functions] of `this` instance,
 * so that for each invoked function,
 * additionally a [Pair]
 * of the name and the arguments of the originally invoked function
 * is emitted.
 */
fun Any.tee(vararg functions: String): Flow<Pair<String, Array<dynamic>>> = callbackFlow {
    var enabled = true
    val callback: (String, Array<dynamic>) -> Unit = { fn: String, args: Array<dynamic> ->
        if (enabled) trySend(fn to args)
    }
    this@tee.tee(*functions, target = callback)
    awaitClose { enabled = false }
}
