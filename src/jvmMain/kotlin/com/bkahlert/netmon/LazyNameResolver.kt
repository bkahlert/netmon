package com.bkahlert.netmon

import com.bkahlert.netmon.mdns.MulticastDnsReverseNameResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class LazyNameResolver {
    private val cache = ConcurrentHashMap<IP, String?>()
    private val scheduledIps = Collections.newSetFromMap(ConcurrentHashMap<IP, Boolean>())
    private val dispatcher = Executors.newFixedThreadPool(4) { runnable ->
        Thread(runnable, "LazyNameResolution")
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(dispatcher)

    private fun schedule(ip: IP) {
        if (scheduledIps.add(ip)) {
            scope.launch {
                MulticastDnsReverseNameResolver.resolve(ip)?.let { resolvedName ->
                    cache[ip] = resolvedName
                }
                scheduledIps.remove(ip)
            }
        }
    }

    fun resolve(ip: IP): String? {
        val name = cache[ip]
        if (name == null) schedule(ip)
        return name
    }

    fun close() {
        dispatcher.close()
    }
}
