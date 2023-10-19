package com.bkahlert.netmon

import com.bkahlert.kommons.FileCache
import com.bkahlert.kommons.Program
import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.logging.logback.StructuredArguments.a
import com.bkahlert.kommons.logging.logback.StructuredArguments.kv
import com.bkahlert.kommons.logging.logback.StructuredArguments.o
import com.bkahlert.kommons.logging.logback.StructuredArguments.v
import com.bkahlert.kommons.text.checkNotBlank
import com.bkahlert.netmon.enrichment.AmazonHostEnricher
import com.bkahlert.netmon.enrichment.AppleHostEnricher
import com.bkahlert.netmon.enrichment.DeviceInfoHostEnricher
import com.bkahlert.netmon.enrichment.HostNameEnricher
import com.bkahlert.netmon.enrichment.HostServicesEnricher
import com.bkahlert.netmon.enrichment.SonosHostEnricher
import com.bkahlert.netmon.logging.LoggingSettings
import com.bkahlert.netmon.mdns.JmDNS
import com.bkahlert.netmon.mdns.JmDNSServiceInfoCache
import com.bkahlert.netmon.model_identification.DeviceModelCodes
import com.bkahlert.netmon.model_identification.load
import com.bkahlert.netmon.model_identification.resource
import com.bkahlert.netmon.mqtt.MqttPublisher
import com.bkahlert.netmon.nmap.NmapMacPrefixesProvisioner
import com.bkahlert.netmon.nmap.NmapNetworkScanner
import com.bkahlert.netmon.scanner.InterfaceResolver
import com.bkahlert.netmon.scanner.InterfaceResolver.Companion.networkInterface
import com.bkahlert.netmon.scanner.NetmonScanner
import com.bkahlert.netmon.scanner.cidr
import com.bkahlert.netmon.serialization.JsonFormat
import java.lang.Thread.interrupted
import java.net.InetAddress
import java.util.Collections
import kotlin.system.exitProcess

class Application {

    private val cache: FileCache by lazy { FileCache.of("netmon") }

    fun start() {

        val localhost = runCatching { InetAddress.getLocalHost() }
            .getOrElse { throw IllegalStateException("Failed to determine localhost", it) }
        val hostname = runCatching { checkNotBlank(localhost.hostName) }
            .getOrElse { throw IllegalStateException("Failed to determine hostname", it) }
        val node = hostname.substringBefore('.').lowercase()

        logger.info("Hostname: {}", kv("hostname", hostname))

        val nmapMacPrefixesProvisioner = NmapMacPrefixesProvisioner(cache)
        val nmapNetworkScanner = NmapNetworkScanner().apply {
            dataDir?.let(nmapMacPrefixesProvisioner::provisionIn)
        }
        val publisher = MqttPublisher(
            host = BrokerSettings.host,
            port = BrokerSettings.port,
            stringFormat = JsonFormat,
            serializer = Event.serializer(),
        )

        val netmons: List<NetmonScanner> = InterfaceResolver().resolve()
            .mapNotNull { interfaceAddress ->
                interfaceAddress.networkInterface?.let { interfaceAddress to it }
            }
            .map { (interfaceAddress, networkInterface) ->
                val topicSubstitutions = mapOf("node" to node, "interface" to networkInterface.name, "cidr" to interfaceAddress.cidr.toString())
                val scanTopic = ScanEventSettings.topic.toString(topicSubstitutions)
                val hostTopic = HostEventSettings.topic.toString(topicSubstitutions)

                val jmDns = JmDNS(interfaceAddress.address, hostname)
                val serviceInfoCache = JmDNSServiceInfoCache(jmDns, serviceTypes = emptyArray())
                Program.onExit {
                    serviceInfoCache.close()
                    jmDns.close()
                }

                NetmonScanner(
                    interfaceAddress = interfaceAddress,
                    scanner = nmapNetworkScanner,
                    enrichers = arrayOf(
                        HostNameEnricher(serviceInfoCache),
                        DeviceInfoHostEnricher(serviceInfoCache),
                        AmazonHostEnricher(serviceInfoCache),
                        SonosHostEnricher(serviceInfoCache),
                        AppleHostEnricher(serviceInfoCache, DeviceModelCodes.load(DeviceModelCodes.resource)),
                        HostServicesEnricher(serviceInfoCache),
                    ),
                    onScan = { scan ->
                        publisher.publish(
                            topic = scanTopic,
                            event = Event.ScanEvent(
                                type = Event.ScanEvent.Type.COMPLETED,
                                hosts = scan.hosts,
                                timestamp = scan.timestamp,
                            ),
                        )
                    },
                    onChange = { host ->
                        publisher.publish(
                            topic = hostTopic,
                            event = Event.HostEvent(
                                type = if (host.status == Status.DOWN) Event.HostEvent.Type.DOWN else Event.HostEvent.Type.UP,
                                host = host,
                            ),
                        )
                    },
                )
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

    companion object {

        private val logger = SLF4J.getLogger("com.bkahlert.netmon.startup")

        @JvmStatic
        fun main(args: Array<out String>) {
            LoggingSettings.apply(*args)
            logger.info("Starting netmon: {}", a(*args, key = "args"))

            val application = Application()
            application.start()
        }
    }
}
