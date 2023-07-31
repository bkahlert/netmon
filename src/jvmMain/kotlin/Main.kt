import ch.qos.logback.classic.Level
import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.logging.logback.Logback
import com.bkahlert.kommons.logging.logback.StructuredArguments.a
import com.bkahlert.kommons.logging.logback.StructuredArguments.kv
import com.bkahlert.kommons.logging.logback.StructuredArguments.o
import com.bkahlert.kommons.text.checkNotBlank
import com.bkahlert.netmon.Event
import com.bkahlert.netmon.JsonFormat
import com.bkahlert.netmon.NetmonScanner
import com.bkahlert.netmon.Network
import com.bkahlert.netmon.Settings
import com.bkahlert.netmon.Status
import com.bkahlert.netmon.levels
import com.bkahlert.netmon.mdns.MulticastDnsResolver
import com.bkahlert.netmon.mqtt.MqttPublisher
import com.bkahlert.netmon.net.InterfaceFilter
import com.bkahlert.netmon.net.cidr
import com.bkahlert.netmon.nmap.NmapNetworkScanner
import net.logstash.logback.argument.StructuredArguments.v
import java.lang.Thread.interrupted
import java.net.InetAddress
import java.net.NetworkInterface
import javax.jmdns.JmDNS

val logger = SLF4J.getLogger("netmon")

fun main(args: Array<String>) {

    Logback.levels(
        "root" to Level.DEBUG,
        "io.netty" to Level.WARN,
        "javax.jmdns" to Level.WARN,
        "com.bkahlert.kommons.exec" to Level.WARN,
        "com.bkahlert.netmon.net" to Level.INFO,
        "com.bkahlert.netmon.nmap" to Level.INFO,
        "com.bkahlert.netmon.mdns" to Level.INFO,
        "com.bkahlert.netmon.mqtt" to Level.WARN,
    )

    logger.info("Starting netmon: {}", a(*args, key = "args"))
    val localhost = runCatching { InetAddress.getLocalHost() }
        .getOrElse { throw IllegalStateException("Failed to determine localhost", it) }
    val hostname = runCatching { checkNotBlank(localhost.hostName) }
        .getOrElse { throw IllegalStateException("Failed to determine hostname", it) }
    val unqualifiedHostname = hostname.substringBefore('.')

    logger.info("Hostname: {}", kv("hostname", hostname))

    val networkInterfaces: List<NetworkInterface> = NetworkInterface.getNetworkInterfaces().toList()
    logger.info("Found {} network {}", v("count", networkInterfaces.size), o("interfaces", networkInterfaces) { it.name })

    val nmapNetworkScanner = NmapNetworkScanner()
    val publisher = MqttPublisher(
        host = Settings.BROKER_HOST,
        port = Settings.Scanner.BROKER_PORT,
        stringFormat = JsonFormat,
        serializer = Event.serializer(),
    )

    val netmons: List<NetmonScanner> = InterfaceFilter.filter(
        networkInterfaces = networkInterfaces,
    ).flatMap { (networkInterface, interfaceAddresses) ->
        interfaceAddresses.map { interfaceAddress ->
            val network = Network(
                hostname = hostname,
                `interface` = networkInterface.name,
                cidr = interfaceAddress.cidr,
            )
            val resolver = MulticastDnsResolver(JmDNS.create(interfaceAddress.address, hostname))

            NetmonScanner(
                network = network.cidr,
                scanner = nmapNetworkScanner,
                resolver = resolver,
                onScan = { scan ->
                    publisher.publish(
                        topic = Settings.SCAN_TOPIC.replaceFirst("+", unqualifiedHostname),
                        event = Event.ScanEvent(
                            network = network,
                            type = Event.ScanEvent.Type.COMPLETED,
                            hosts = scan.hosts,
                            timestamp = scan.timestamp,
                        ),
                    )
                },
                onChange = { host ->
                    publisher.publish(
                        topic = Settings.HOST_TOPIC.replaceFirst("+", unqualifiedHostname),
                        event = Event.HostEvent(
                            network = network,
                            type = if (host.status == Status.DOWN) Event.HostEvent.Type.DOWN else Event.HostEvent.Type.UP,
                            host = host,
                        ),
                    )
                },
            )
        }
    }

    logger.info("Starting {} netmon(s) for {}", v("count", netmons.size), o("networks", netmons) { it.network })
    netmons.forEach { it.start() }

    while (!interrupted() && netmons.any { it.isAlive }) {
        try {
            Thread.sleep(1000) // Sleep for 1 second
        } catch (e: InterruptedException) {
            // Restore the interrupted status so we exit the loop
            Thread.currentThread().interrupt()
        }
    }

    logger.info("Done.")
}
