import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.logging.logback.StructuredArguments.a
import com.bkahlert.kommons.logging.logback.StructuredArguments.kv
import com.bkahlert.kommons.logging.logback.StructuredArguments.o
import com.bkahlert.kommons.text.checkNotBlank
import com.bkahlert.netmon.BrokerSettings
import com.bkahlert.netmon.Event
import com.bkahlert.netmon.HostEventSettings
import com.bkahlert.netmon.JsonFormat
import com.bkahlert.netmon.LazyNameResolver
import com.bkahlert.netmon.NetmonScanner
import com.bkahlert.netmon.ScanEventSettings
import com.bkahlert.netmon.Status
import com.bkahlert.netmon.logging.Verbosity
import com.bkahlert.netmon.mdns.JmDNS
import com.bkahlert.netmon.mdns.MulticastDnsResolver
import com.bkahlert.netmon.mdns.MulticastDnsReverseNameResolver
import com.bkahlert.netmon.mqtt.MqttPublisher
import com.bkahlert.netmon.net.InterfaceFilter
import com.bkahlert.netmon.net.cidr
import com.bkahlert.netmon.nmap.NmapNetworkScanner
import net.logstash.logback.argument.StructuredArguments.v
import java.lang.Thread.interrupted
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections
import kotlin.system.exitProcess

val logger = SLF4J.getLogger("com.bkahlert.netmon.startup")

fun main(args: Array<String>) {
    Verbosity.from(*args).apply()

    logger.info("Starting netmon: {}", a(*args, key = "args"))
    val localhost = runCatching { InetAddress.getLocalHost() }
        .getOrElse { throw IllegalStateException("Failed to determine localhost", it) }
    val hostname = runCatching { checkNotBlank(localhost.hostName) }
        .getOrElse { throw IllegalStateException("Failed to determine hostname", it) }
    val node = hostname.substringBefore('.').lowercase()

    logger.info("Hostname: {}", kv("hostname", hostname))

    val networkInterfaces: List<NetworkInterface> = NetworkInterface.getNetworkInterfaces().toList()
    logger.info("Found {} network {}", v("count", networkInterfaces.size), o("interfaces", networkInterfaces) { it.name })

    val nmapNetworkScanner = NmapNetworkScanner()
    val publisher = MqttPublisher(
        host = BrokerSettings.host,
        port = BrokerSettings.port,
        stringFormat = JsonFormat,
        serializer = Event.serializer(),
    )

    val netmons: List<NetmonScanner> = InterfaceFilter.filter(
        networkInterfaces = networkInterfaces,
    )
        .flatMap { (networkInterface, interfaceAddresses) ->
            interfaceAddresses.map { interfaceAddress ->
                val scanTopic = ScanEventSettings.topic
                    .replaceFirst("\${node}", node)
                    .replaceFirst("\${interface}", networkInterface.name)
                    .replaceFirst("\${cidr}", interfaceAddress.cidr.toString())
                val hostTopic = HostEventSettings.topic
                    .replaceFirst("\${node}", node)
                    .replaceFirst("\${interface}", networkInterface.name)
                    .replaceFirst("\${cidr}", interfaceAddress.cidr.toString())

                val resolver = MulticastDnsResolver(
                    jmdns = JmDNS(addr = interfaceAddress.address, name = hostname),
                    fallbackResolver = LazyNameResolver(MulticastDnsReverseNameResolver, nmapNetworkScanner),
                )

                var firstScanPublished = true
                var firstHostPublished = true

                NetmonScanner(
                    `interface` = networkInterface.name,
                    cidr = interfaceAddress.cidr,
                    scanner = nmapNetworkScanner,
                    resolver = resolver,
                    onScan = { scan ->
                        publisher.publish(
                            topic = scanTopic,
                            event = Event.ScanEvent(
                                type = Event.ScanEvent.Type.COMPLETED,
                                hosts = scan.hosts,
                                timestamp = scan.timestamp,
                            ),
                        ).also { success ->
                            if (firstScanPublished) {
                                firstScanPublished = false
                                if (success) {
                                    logger.info(
                                        "First scan on {} of {} with {} hosts successfully published to {}",
                                        v("interface", networkInterface.name),
                                        v("cidr", interfaceAddress.cidr),
                                        v("count", scan.hosts.size),
                                        v("topic", scanTopic)
                                    )
                                } else {
                                    logger.error(
                                        "First scan on {} of {} with {} hosts failed to publish to {}",
                                        v("interface", networkInterface.name),
                                        v("cidr", interfaceAddress.cidr),
                                        v("count", scan.hosts.size),
                                        v("topic", scanTopic)
                                    )
                                }
                            }
                        }
                    },
                    onChange = { host ->
                        publisher.publish(
                            topic = hostTopic,
                            event = Event.HostEvent(
                                type = if (host.status == Status.DOWN) Event.HostEvent.Type.DOWN else Event.HostEvent.Type.UP,
                                host = host,
                            ),
                        ).also { success ->
                            if (firstHostPublished) {
                                firstHostPublished = false
                                if (success) {
                                    logger.info(
                                        "First host state change on {} of {} successfully published to {}: {}",
                                        v("interface", networkInterface.name),
                                        v("cidr", interfaceAddress.cidr),
                                        v("topic", scanTopic),
                                        v("host", host),
                                    )
                                } else {
                                    logger.error(
                                        "First host state change on {} of {} failed to publish to {}: {}",
                                        v("interface", networkInterface.name),
                                        v("cidr", interfaceAddress.cidr),
                                        v("topic", scanTopic),
                                        v("host", host),
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }

    logger.info("Starting {} netmon(s) for {}", v("count", netmons.size), o("networks", netmons) { it.cidr })

    val failed = Collections.synchronizedList<NetmonScanner>(mutableListOf())
    val h = Thread.UncaughtExceptionHandler { th, ex ->
        logger.error("Uncaught exception in thread ${th.name}", ex)
        failed.add(th as NetmonScanner)
        Thread.currentThread().interrupt()
    }
    netmons.forEach {
        it.setUncaughtExceptionHandler(h)
        it.start()
    }

    while (!interrupted() && netmons.all { it.isAlive }) {
        try {
            Thread.sleep(1000) // Sleep for 1 second
        } catch (e: InterruptedException) {
            // Restore the interrupted status so we exit the loop
            Thread.currentThread().interrupt()
        }
    }

    netmons.filter { it.isAlive }.forEach { it.interrupt() }

    if (failed.isEmpty()) {
        logger.info("All {} netmon(s) for {} stopped", v("count", netmons.size), o("networks", netmons) { it.cidr })
        exitProcess(0)
    } else {
        logger.error("Failed to start {} netmon(s) for {}", v("count", failed.size), o("networks", failed) { it.cidr })
        exitProcess(1)
    }
}
