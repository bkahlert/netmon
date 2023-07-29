import ch.qos.logback.classic.Level
import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.logging.logback.Logback
import com.bkahlert.kommons.logging.logback.StructuredArguments.a
import com.bkahlert.kommons.logging.logback.StructuredArguments.kv
import com.bkahlert.kommons.logging.logback.StructuredArguments.o
import com.bkahlert.netmon.Event
import com.bkahlert.netmon.JsonFormat
import com.bkahlert.netmon.MqttPublisher
import com.bkahlert.netmon.NetmonScanner
import com.bkahlert.netmon.Network
import com.bkahlert.netmon.NmapNetworkScanner
import com.bkahlert.netmon.Settings
import com.bkahlert.netmon.Status
import com.bkahlert.netmon.cidr
import com.bkahlert.netmon.levels
import com.bkahlert.netmon.maxHosts
import net.logstash.logback.argument.StructuredArguments.v
import java.lang.Thread.interrupted
import java.math.BigInteger
import java.net.Inet4Address
import java.net.Inet6Address
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

    val networkInterfaces = NetworkInterface.getNetworkInterfaces().toList()
    logger.info("Found {} network {}", v("count", networkInterfaces.size), o("interfaces", networkInterfaces) { it.name })
    val networks = networkInterfaces
        .filter { iface ->
            iface.isUp.also {
                if (!it) logger.info("Skipping {} because it's down.", kv("interface", iface))
            }
        }
        .filterNot { iface ->
            iface.isLoopback.also {
                if (it) logger.info("Skipping {} because it's a loopback interface.", kv("interface", iface))
            }
        }
        .flatMap { iface: NetworkInterface ->
            val ifaceAddresses = iface.interfaceAddresses.toList()
            logger.debug("Checking {} of {}", o("addresses", ifaceAddresses), kv("interface", iface))
            ifaceAddresses
                .filter { ifaceAddress ->
                    when (val inetAddress = ifaceAddress.address) {
                        is Inet4Address -> {
                            inetAddress.isSiteLocalAddress.also {
                                if (!it) logger.debug("Skipping {} because it's not site-local.", kv("interfaceAddress", ifaceAddress))
                            }
                        }

                        is Inet6Address -> {
                            inetAddress.isLinkLocalAddress.also {
                                if (!it) logger.debug("Skipping {} because it's not link-local.", kv("interfaceAddress", ifaceAddress))
                            }
                        }

                        else -> {
                            logger.debug("Skipping {} because it's no supported IP protocol.", kv("interfaceAddress", ifaceAddress))
                            false
                        }
                    }
                }
                .filter { ifaceAddress ->
                    (ifaceAddress.maxHosts in hostCountRange).also {
                        if (!it) logger.debug(
                            "Skipping {} because it's {} is not in {}",
                            kv("interfaceAddress", ifaceAddress),
                            kv("max-host-count", ifaceAddress.maxHosts),
                            kv("allowed-host-count-range", hostCountRange),
                        )
                    }
                }
                .also {
                    if (it.isEmpty()) logger.info("Skipping {} because no eligible interface address was found.", kv("interface", iface))
                }
                .map { ifaceAddress ->
                    Network(
                        hostname = hostname,
                        `interface` = iface.name,
                        cidr = ifaceAddress.cidr,
                    )
                }
        }

    logger.info("Found {}", o("networks", networks))

    val scanner = NmapNetworkScanner()
    val publisher = MqttPublisher(
        host = Settings.brokerHost,
        port = Settings.Scanner.brokerPort,
        stringFormat = JsonFormat,
        serializer = Event.serializer(),
    )

    val netmons: List<NetmonScanner> = networks.map { network ->
        NetmonScanner(
            network = network.cidr,
            scanner = scanner,
            onScan = { scan ->
                publisher.publish(
                    topic = "dt/netmon/home/scan",
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
                    topic = "dt/netmon/home/host",
                    event = Event.HostEvent(
                        network = network,
                        type = if (host.status == Status.DOWN) Event.HostEvent.Type.DOWN else Event.HostEvent.Type.UP,
                        host = host,
                    ),
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
