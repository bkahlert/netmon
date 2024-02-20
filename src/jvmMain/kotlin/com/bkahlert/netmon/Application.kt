package com.bkahlert.netmon

import com.bkahlert.kommons.FileCache
import com.bkahlert.kommons.Pid
import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.logging.logback.StructuredArguments.kv
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
import com.bkahlert.netmon.net.SystemInterfaceAddressResolver
import com.bkahlert.netmon.net.cidr
import com.bkahlert.netmon.net.networkInterface
import com.bkahlert.netmon.nmap.NmapMacPrefixesProvisioner
import com.bkahlert.netmon.nmap.NmapNetworkScanner
import com.bkahlert.netmon.nmap.NmapSettings
import com.bkahlert.netmon.scanner.NetmonScanner
import com.bkahlert.netmon.scanner.NetworkFilterSettings
import com.bkahlert.netmon.scanner.ScannerSettings
import com.bkahlert.netmon.serialization.JsonFormat
import java.net.InetAddress
import java.net.InterfaceAddress
import java.util.Collections
import kotlin.system.exitProcess

class Application(
    private val hostname: String = kotlin.runCatching { InetAddress.getLocalHost() }
        .getOrElse { throw IllegalStateException("Failed to determine localhost", it) }
        .let { localhost ->
            checkNotBlank(localhost.hostName)
            kotlin.runCatching { checkNotBlank(localhost.hostName) }
                .getOrElse { throw IllegalStateException("Failed to determine hostname", it) }
        },
    private val interfaceAddresses: () -> Iterable<InterfaceAddress> = SystemInterfaceAddressResolver()::resolve,
) {

    private val cache: FileCache by lazy { FileCache.of("netmon") }
    private val nmapMacPrefixesProvisioner: NmapMacPrefixesProvisioner by lazy { NmapMacPrefixesProvisioner(cache) }
    private val nmapNetworkScanner by lazy {
        NmapNetworkScanner().apply {
            dataDir?.let(nmapMacPrefixesProvisioner::provisionIn)
        }
    }

    fun start() {
        logger.info(
            "Configuration: {}",
            listOf(
                "hostname" to hostname,
                "cache" to cache,
            ).joinToString(separator = "") { (key, value) -> "\n${key.padStart(30)}: $value" },
        )

        logger.info(
            "Settings: {}",
            listOf(
                LoggingSettings,
                NetworkFilterSettings,
                ScannerSettings,
                NmapSettings,
                BrokerSettings,
                ScanEventSettings,
                HostEventSettings,
            ).joinToString(separator = "") { settings ->
                "\n${settings::class.simpleName.orEmpty().padStart(30)}: ${settings.toString().substringAfter('[').substringBeforeLast(']')}"
            },
        )

        val namedInterfaceAddresses: () -> Iterable<Pair<InterfaceAddress, String>> = {
            interfaceAddresses()
                .mapNotNull { interfaceAddress ->
                    interfaceAddress.networkInterface?.let { interfaceAddress to it.name }
                }
        }.also {
            logger.info(
                "Interface addresses found: {}",
                it().joinToString { (interfaceAddress, interfaceName) ->
                    "$interfaceName:${interfaceAddress.cidr}"
                },
            )
        }

        val publisher = MqttPublisher(
            host = BrokerSettings.host,
            port = BrokerSettings.port,
            stringFormat = JsonFormat,
            serializer = Event.serializer(),
        ).also {
            logger.info("{} connected", v(it))
        }


        val serviceInfoCaches = Collections.synchronizedMap(mutableMapOf<InetAddress, JmDNSServiceInfoCache>())
        val scanners = Collections.synchronizedMap(mutableMapOf<InetAddress, NetmonScanner>())

        val application = SlicedApplication(
            slice = namedInterfaceAddresses,
            start = { (interfaceAddress, interfaceName) ->
                val serviceInfoCache = serviceInfoCaches.getOrPut(interfaceAddress.address) {
                    JmDNSServiceInfoCache(JmDNS(interfaceAddress.address, hostname), serviceTypes = emptyArray())
                }

                val topicSubstitutions = mapOf("node" to hostname, "interface" to interfaceName, "cidr" to interfaceAddress.cidr.toString())
                val scanTopic = ScanEventSettings.topic.toString(topicSubstitutions)
                val hostTopic = HostEventSettings.topic.toString(topicSubstitutions)

                scanners.getOrPut(interfaceAddress.address) {
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

                            logger.info(
                                "Scan {}:{} with {} host(s) completed and published to {}: {}",
                                interfaceName, interfaceAddress.cidr,
                                scan.hosts.size,
                                scanTopic,
                                scan.hosts.joinToString(limit = 4) { it.ip.toString() }
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
            },
            process = { (interfaceAddress, _) ->
                scanners.getValue(interfaceAddress.address).scan()
                Thread.sleep(ScannerSettings.pauseDuration.inWholeMilliseconds)
            },
            finalize = { (interfaceAddress, interfaceName) ->
                serviceInfoCaches.remove(interfaceAddress.address)?.also {
                    it.close()
                    it.jmDns.close()
                }.also { stoppedCache ->
                    if (stoppedCache != null) {
                        logger.info("Stopped scanning {}:{} and corresponding cache", interfaceName, interfaceAddress.cidr)
                    } else {
                        logger.warn("Stopped scanning {}:{} but no corresponding cache found", interfaceName, interfaceAddress.cidr)
                    }
                }
            },
        )

        val failed = application.start().waitForTermination().failed
        check(failed.isEmpty()) { "Errors occurred scanning the following ${failed.size} network(s): ${failed.joinToString { it.first.cidr }}" }
    }

    companion object {

        private val logger by SLF4J

        @JvmStatic
        fun main(args: Array<out String>) {
            try {
                LoggingSettings.apply(*args)
                logger.info("Application starting with {} and {}", kv("pid", Pid.current.value), kv("args", args.asList()))
                Application().start()
                logger.debug("Application terminated successfully")
                exitProcess(0)
            } catch (e: Throwable) {
                logger.error("Application terminated erroneously", e)
                exitProcess(1)
            }
        }
    }
}
