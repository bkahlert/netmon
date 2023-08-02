package com.bkahlert.netmon

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * A name resolver that resolves names lazily by delegating
 * requests to the long-running [nameResolvers].
 *
 * First-time requests always resolve `null` but trigger
 * name resolutions that are cached for subsequent requests.
 */
class LazyNameResolver(
    private vararg val nameResolvers: NameResolver,
) : NameResolver, AutoCloseable {
    private val cache = ConcurrentHashMap<IP, String?>()
    private val scheduledIps = Collections.newSetFromMap(ConcurrentHashMap<IP, Boolean>())
    private val dispatcher = Executors.newFixedThreadPool(4) { runnable ->
        Thread(runnable, "LazyNameResolution")
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(dispatcher)

    private fun schedule(ip: IP) {
        if (scheduledIps.add(ip)) {
            scope.launch {
                nameResolvers
                    .firstNotNullOfOrNull { it.resolve(ip) }
                    ?.let { resolvedName -> cache[ip] = resolvedName }
                scheduledIps.remove(ip)
            }
        }
    }

    override fun resolve(ip: IP): String? {
        val name = cache[ip]
        if (name == null) schedule(ip)
        return name
    }

    override fun close() {
        dispatcher.close()
    }
}

fun interface NameResolver {
    fun resolve(ip: IP): String?
}
