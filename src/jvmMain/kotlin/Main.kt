import ch.qos.logback.classic.Level
import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.logging.logback.Logback
import com.bkahlert.kommons.logging.logback.StructuredArguments.a
import com.bkahlert.kommons.logging.logback.StructuredArguments.kv
import com.bkahlert.netmon.JsonFormat
import com.bkahlert.netmon.MqttPublisher
import com.bkahlert.netmon.NetmonScanner
import com.bkahlert.netmon.NmapNetworkScanner
import com.bkahlert.netmon.ScanEvent
import com.bkahlert.netmon.Settings
import com.bkahlert.netmon.Status
import com.bkahlert.netmon.cidr
import com.bkahlert.netmon.levels
import com.bkahlert.netmon.maxHosts
import com.bkahlert.netmon.scanElligable
import java.lang.Thread.interrupted
import java.math.BigInteger
import java.net.InetAddress
import java.net.NetworkInterface

val logger = SLF4J.getLogger("netmon")

fun main(args: Array<String>) {
    //requireCommand("avahi-resolve", installationPackage = "avahi-utils")
    //avahi-resolve -a 192.168.16.33

    Logback.levels(
        "root" to Level.DEBUG,
        "io.netty" to Level.WARN,
        "com.bkahlert.kommons.exec" to Level.WARN,
    )

    logger.info("Starting netmon: {}", a(*args, key = "args"))
    val hostname = runCatching { InetAddress.getLocalHost().hostName }
        .getOrElse { throw IllegalStateException("Failed to determine hostname", it) }

    logger.info("Hostname: {}", kv("hostname", hostname))

    val hostCountRange = BigInteger(Settings.minHosts)..BigInteger(Settings.maxHosts)
    val networks = NetworkInterface.getNetworkInterfaces()
        .asSequence()
        .filter { it.isUp }
        .filterNot { it.isLoopback }
        .mapNotNull { networkInterface: NetworkInterface ->
            networkInterface.interfaceAddresses.toList()
                .filter { it.scanElligable }
                .filter { it.maxHosts in hostCountRange }
                .maxByOrNull { it.maxHosts }
                ?.cidr
        }
        .toList()

    logger.info("Found networks: {}", networks)

    val scanner = NmapNetworkScanner()
    val publisher = MqttPublisher(
        host = Settings.brokerHost,
        port = Settings.Scanner.brokerPort,
        stringFormat = JsonFormat,
        serializer = ScanEvent.serializer(),
    )

    val netmons: List<NetmonScanner> = networks.map { network ->
        NetmonScanner(
            network = network,
            scanner = scanner,
            onScan = { scan ->
                publisher.publish(
                    topic = "dt/netmon/home/scans",
                    event = ScanEvent.ScanCompletedEvent(source = hostname, scan = scan),
                )
            },
            onChange = { host ->
                publisher.publish(
                    topic = "dt/netmon/home/updates",
                    event = if (host.status == Status.DOWN) ScanEvent.HostDownEvent(source = hostname, host = host)
                    else ScanEvent.HostUpEvent(source = hostname, host = host),
                )
            },
        ).apply { start() }
    }

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
