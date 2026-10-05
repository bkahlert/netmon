package com.bkahlert.netmon.router

import com.bkahlert.netmon.mdns.MdnsLookup
import com.bkahlert.netmon.router.HostListParser.toRouterHost
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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
 * [byMac] and [byIp] never touch the network: they answer from what the daemon thread has loaded, stale or not, else
 * `null`. Without credentials, a missing or expired MAC is queued once and looked up by that thread, one MAC after the
 * other; after a transport failure the rest of that batch is dropped and asked for again by the next [byMac] call.
 *
 * A failed refresh keeps the last table. Two entries may share an IP; [byIp] returns the active one. Failures are logged
 * once, when they begin, and recovery once.
 */
class FritzBoxHosts(
    private val client: () -> Tr064Client?,
    private val credentials: Credentials?,
    private val clock: Clock = Clock.System,
    private val refreshEvery: Duration = 60.seconds,
    private val logger: Logger = LoggerFactory.getLogger(FritzBoxHosts::class.java),
) : AutoCloseable, RouterHostLookup {

    private class Table(val byMac: Map<String, RouterHost>, val byIp: Map<String, RouterHost>)

    @Volatile
    private var table = Table(emptyMap(), emptyMap())
    private val lookups = ConcurrentHashMap<String, Pair<RouterHost?, Instant>>()
    private val pending = ConcurrentHashMap.newKeySet<String>()
    private val queue = LinkedBlockingQueue<String>()

    @Volatile
    private var failing = false

    @Volatile
    private var closed = false

    @Volatile
    private var worker: Thread? = null

    override fun byMac(mac: String): RouterHost? {
        val key = mac.lowercase()
        table.byMac[key]?.let { return it }
        if (credentials != null) return null
        val cached = lookups[key]
        if (!closed && (cached == null || clock.now() - cached.second >= LOOKUP_TTL) && pending.add(key)) {
            queue.add(key)
            start()
        }
        return cached?.first
    }

    override fun byIp(ip: String): RouterHost? = table.byIp[ip]

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

    /** Starts the daemon thread: it refreshes the table with credentials, else answers the queued lookups. */
    @Synchronized
    fun start() {
        if (worker != null || closed) return
        worker = thread(name = "fritzbox-hosts", isDaemon = true) {
            if (credentials != null) refreshLoop() else lookupLoop()
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

    private fun refreshLoop() {
        while (!closed && !Thread.currentThread().isInterrupted) {
            refresh()
            try {
                Thread.sleep(refreshEvery.inWholeMilliseconds)
            } catch (e: InterruptedException) {
                return
            }
        }
    }

    private fun lookupLoop() {
        while (!closed && !Thread.currentThread().isInterrupted) {
            val batch = ArrayList<String>()
            try {
                batch.add(queue.take())
            } catch (e: InterruptedException) {
                return
            }
            queue.drainTo(batch)
            var transportFailed = false
            for (mac in batch) {
                if (!transportFailed) transportFailed = !lookup(mac)
                pending.remove(mac)
            }
        }
    }

    /** Looks [mac] up and caches the answer; returns `false` after a transport or authentication failure. */
    private fun lookup(mac: String): Boolean {
        try {
            val client = client() ?: return true
            val fields = client.hosts("GetSpecificHostEntry", mapOf("MACAddress" to mac.uppercase()))
            lookups[mac] = (fields + ("MACAddress" to mac)).toRouterHost() to clock.now()
        } catch (e: Tr064Exception) {
            if (e.fault != UNKNOWN_ENTRY) {
                report(e)
                return false
            }
            lookups[mac] = null to clock.now()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return true
        } catch (e: Exception) {
            report(e)
            return false
        }
        if (failing) logger.info("FRITZ!Box host lookup works again")
        failing = false
        return true
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

    private fun report(e: Exception) {
        if (!failing) logger.warn("FRITZ!Box host table not readable: {}", e.toString().replace(SESSION_ID, "sid=***"))
        failing = true
    }

    companion object {
        private val LOOKUP_TTL = 10.minutes
        private const val CLOSE_WAIT_MILLIS = 2_000L

        /** What the box answers when it knows no host with the asked MAC; a normal answer, not a failure. */
        private const val UNKNOWN_ENTRY = "NoSuchEntryInArray"
        private val SESSION_ID = Regex("sid=[^&\\s\"']+")
    }
}
