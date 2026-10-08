package com.bkahlert.netmon.scanner.discovery.ssdp

import com.bkahlert.netmon.contract.IP
import com.bkahlert.netmon.scanner.support.logging.SLF4J
import com.bkahlert.netmon.scanner.support.net.BoundedInputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.toJavaDuration

/** What the identity rules need from SSDP. */
interface SsdpLookup {
    /** Returns the description of the device at [ip], or `null` if none was announced within the TTL. */
    fun device(ip: IP): DeviceDescription?
}

/**
 * UPnP device descriptions by the IP that announced them.
 *
 * [offer] records an announcement; a location not seen before is fetched once, and a location that failed is left
 * alone for [ttl]. An entry that no announcement of its location renewed within [ttl] is gone, and an `ssdp:byebye`
 * removes it. [listen] joins the SSDP group on an interface, searches at start and every `searchEvery`, and offers
 * every message received. [device] never touches the network. All methods are thread-safe.
 */
class SsdpCache(
    private val fetch: (URI) -> DeviceDescription?,
    private val clock: Clock = Clock.System,
    private val ttl: Duration = 30.minutes,
) : SsdpLookup {

    private val logger by SLF4J

    private class Fetched(val description: DeviceDescription?, val at: Instant)

    // The location is kept so that a different, failing location announced from a reused IP does not renew this entry.
    private class Entry(val description: DeviceDescription, val location: String, val announcedAt: Instant)

    private val byIp = ConcurrentHashMap<IP, Entry>()
    private val locations = ConcurrentHashMap<String, Fetched>()

    override fun device(ip: IP): DeviceDescription? = byIp[ip]?.takeIf { clock.now() - it.announcedAt < ttl }?.description

    /**
     * Records [message] as sent by [from], fetching its location on the calling thread if it is not known yet.
     *
     * Only an `http` location whose host is [from] as an address literal is used; any other is ignored. At most 512
     * locations are kept; while that many are fresh, a new location is ignored.
     */
    fun offer(message: SsdpMessage, from: IP) {
        if (message.isByebye) {
            byIp.remove(from)
            return
        }
        val location = message.location ?: return
        val uri = runCatching { URI(location) }.getOrNull()?.takeIf { it.scheme.equals("http", ignoreCase = true) && literal(it.host) == from } ?: return
        val now = clock.now()
        val known = locations[location]?.takeIf { now - it.at < ttl }
        val description = if (known != null) known.description else {
            if (!synchronized(locations) { fits(location, now) }) return
            byIp.values.removeIf { now - it.announcedAt >= ttl }
            fetchOnce(location, uri, now)
        }
        if (description != null) byIp[from] = Entry(description, location, now)
    }

    // Callers hold the lock on `locations`, so concurrent offers cannot together exceed the limit.
    private fun fits(location: String, now: Instant): Boolean {
        if (locations.containsKey(location) || locations.size < MAX_LOCATIONS) return true
        locations.values.removeIf { now - it.at >= ttl }
        return locations.size < MAX_LOCATIONS
    }

    private fun fetchOnce(location: String, uri: URI, now: Instant): DeviceDescription? {
        val description = try {
            fetch(uri)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return null
        } catch (e: Exception) {
            logger.debug("Description at {} unreadable: {}", uri, e.toString())
            null
        }
        synchronized(locations) {
            if (fits(location, now)) locations[location] = Fetched(description, now)
        }
        return description
    }

    // Parses only literals, so a host name never causes a DNS lookup: a bracketed host is an IPv6 literal by URI syntax.
    private fun literal(host: String?): IP? = when {
        host == null -> null
        host.startsWith("[") -> runCatching { IP.of(InetAddress.getByName(host).address) }.getOrNull()
        else -> host.split('.')
            .takeIf { parts -> parts.size == 4 && parts.all { part -> part.length in 1..3 && part.all { it in '0'..'9' } } }
            ?.map { it.toInt() }
            ?.takeIf { octets -> octets.all { it <= 255 } }
            ?.let { octets -> IP.of(ByteArray(4) { octets[it].toByte() }) }
    }

    /**
     * Starts a daemon thread that listens on [networkInterface]; closing the result stops it.
     *
     * If the SSDP group cannot be joined on [networkInterface], a warning is logged and the result does nothing.
     */
    fun listen(networkInterface: NetworkInterface, searchEvery: Duration = 10.minutes): AutoCloseable {
        val group = InetSocketAddress(SSDP_GROUP, SSDP_PORT)
        val socket = try {
            MulticastSocket(null)
        } catch (e: IOException) {
            logger.warn("SSDP unavailable on {}: {}", networkInterface.name, e.toString())
            return AutoCloseable {}
        }
        try {
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(SSDP_PORT))
            socket.networkInterface = networkInterface
            socket.joinGroup(group, networkInterface)
            socket.soTimeout = 1000
        } catch (e: Exception) {
            socket.close()
            logger.warn("SSDP unavailable on {}: {}", networkInterface.name, e.toString())
            return AutoCloseable {}
        }
        val worker = thread(name = "ssdp-${networkInterface.name}", isDaemon = true) {
            val buffer = ByteArray(8192)
            var nextSearch = Instant.DISTANT_PAST
            while (!socket.isClosed && !Thread.currentThread().isInterrupted) {
                val now = clock.now()
                if (now >= nextSearch) {
                    try {
                        socket.send(DatagramPacket(SEARCH, SEARCH.size, group))
                    } catch (e: IOException) {
                        logger.debug("SSDP search on {} failed: {}", networkInterface.name, e.toString())
                    }
                    nextSearch = now + searchEvery
                }
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (e: SocketTimeoutException) {
                    continue
                } catch (e: IOException) {
                    if (!socket.isClosed) logger.warn("SSDP listening on {} stopped: {}", networkInterface.name, e.toString())
                    break
                }
                val text = String(packet.data, packet.offset, packet.length, Charsets.ISO_8859_1)
                SsdpMessage.parse(text)?.let { offer(it, IP.of(packet.address.address)) }
            }
        }
        return AutoCloseable {
            socket.close()
            worker.interrupt()
            worker.join(CLOSE_WAIT_MILLIS)
        }
    }

    companion object {
        const val SSDP_GROUP = "239.255.255.250"
        const val SSDP_PORT = 1900
        private const val CLOSE_WAIT_MILLIS = 2_000L
        private const val MAX_LOCATIONS = 512
        private val SEARCH = "M-SEARCH * HTTP/1.1\r\nHOST: $SSDP_GROUP:$SSDP_PORT\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: ssdp:all\r\n\r\n".toByteArray()
    }
}

/**
 * Reads a description over HTTP/1.1, or returns `null` if the location answers other than 200.
 *
 * @throws IOException if the location cannot be reached, sends no headers within [timeout], sends more than [maxBytes],
 *   or does not finish the body within [timeout] of the headers.
 */
class DescriptionFetcher(
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3)).build(),
    private val maxBytes: Int = 256 * 1024,
    private val timeout: Duration = 3.seconds,
) : (URI) -> DeviceDescription? {

    override fun invoke(location: URI): DeviceDescription? {
        val request = HttpRequest.newBuilder(location)
            .version(HttpClient.Version.HTTP_1_1)
            .timeout(timeout.toJavaDuration())
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
        val body = BoundedInputStream(response.body(), maxBytes.toLong(), timeout.toJavaDuration()).use { body ->
            if (response.statusCode() != 200) return null
            body.readAllBytes()
        }
        return DeviceDescription.parse(body.inputStream())
    }
}
