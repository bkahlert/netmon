package com.bkahlert.netmon.router

import com.bkahlert.netmon.logging.SLF4J
import com.bkahlert.netmon.mdns.MdnsLookup
import com.bkahlert.netmon.router.HostListParser.toRouterHost
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Where the FRITZ!Box answers TR-064: the setting, else the `_tr064._tcp` record's IPv4 and port, else `fritz.box`. */
object FritzBoxEndpoint {
    fun discover(setting: String?, mdns: MdnsLookup): URI? {
        setting?.takeIf { it.isNotBlank() }?.let { return URI(it.trimEnd('/')) }
        mdns.services("tr064").firstOrNull()?.let { record ->
            val ipv4 = record.properties["ipv4"]?.text ?: record.inet4Addresses.firstOrNull()?.hostAddress
            val port = record.serviceRecord?.port ?: 49000
            if (ipv4 != null) return URI("http://$ipv4:$port")
        }
        return URI("http://fritz.box:49000")
    }
}

/**
 * The FRITZ!Box host table, refreshed every [refreshEvery] on a daemon thread when [credentials] are set, else looked
 * up per MAC without authentication and kept for ten minutes.
 *
 * A failed refresh keeps the last table. Two entries may share an IP; [byIp] returns the active one.
 */
class FritzBoxHosts(
    private val client: () -> Tr064Client?,
    private val credentials: Credentials?,
    private val clock: Clock = Clock.System,
    private val refreshEvery: Duration = 60.seconds,
) : AutoCloseable {

    private val logger by SLF4J

    private class Table(val byMac: Map<String, RouterHost>, val byIp: Map<String, RouterHost>)

    @Volatile
    private var table = Table(emptyMap(), emptyMap())
    private val lookups = ConcurrentHashMap<String, Pair<RouterHost?, Instant>>()

    @Volatile
    private var failing = false

    @Volatile
    private var closed = false
    private var worker: Thread? = null

    fun byMac(mac: String): RouterHost? {
        val key = mac.lowercase()
        table.byMac[key]?.let { return it }
        if (credentials != null) return null
        val now = clock.now()
        lookups[key]?.takeIf { (_, at) -> now - at < LOOKUP_TTL }?.let { return it.first }
        val host = try {
            lookup(key)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        } catch (e: Exception) {
            report(e)
            null
        }
        lookups[key] = host to now
        return host
    }

    fun byIp(ip: String): RouterHost? = table.byIp[ip]

    /** Loads the table once; with no credentials, there is nothing to load. Any failure keeps the last table. */
    fun refresh() {
        if (credentials == null) return
        try {
            val client = client() ?: return
            val path = client.hosts("X_AVM-DE_GetHostListPath").getValue("X_AVM-DE_HostListPath")
            val hosts = client.get(path).use(HostListParser::parse)
            table = Table(
                byMac = index(hosts) { it.mac },
                byIp = index(hosts) { it.ip },
            )
            if (failing) logger.info("FRITZ!Box host table readable again: {} hosts", hosts.size)
            failing = false
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            report(e)
        }
    }

    @Synchronized
    fun start() {
        if (worker != null || closed || credentials == null) return
        worker = thread(name = "fritzbox-hosts", isDaemon = true) {
            while (!closed && !Thread.currentThread().isInterrupted) {
                refresh()
                try {
                    Thread.sleep(refreshEvery.inWholeMilliseconds)
                } catch (e: InterruptedException) {
                    return@thread
                }
            }
        }
    }

    @Synchronized
    override fun close() {
        closed = true
        worker?.let {
            it.interrupt()
            it.join(CLOSE_WAIT_MILLIS)
        }
        worker = null
    }

    /** Keys the hosts by [key]; of several entries with one key an active one wins over an inactive one. */
    private fun index(hosts: List<RouterHost>, key: (RouterHost) -> String?): Map<String, RouterHost> {
        val result = HashMap<String, RouterHost>()
        for (host in hosts) {
            val k = key(host) ?: continue
            val current = result[k]
            if (current == null || host.active || !current.active) result[k] = host
        }
        return result
    }

    private fun lookup(mac: String): RouterHost? {
        val client = client() ?: return null
        val fields = client.hosts("GetSpecificHostEntry", mapOf("MACAddress" to mac.uppercase()))
        return (fields + ("MACAddress" to mac)).toRouterHost()
    }

    private fun report(e: Exception) {
        if (!failing) logger.warn("FRITZ!Box host table not readable: {}", e.toString().replace(SESSION_ID, "sid=***"))
        failing = true
    }

    companion object {
        private val LOOKUP_TTL = 10.minutes
        private const val CLOSE_WAIT_MILLIS = 2_000L
        private val SESSION_ID = Regex("sid=[^&\\s\"']+")
    }
}
