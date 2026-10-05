package com.bkahlert.netmon

import com.bkahlert.kommons.FileCache
import com.bkahlert.kommons.Pid
import com.bkahlert.netmon.logging.SLF4J
import com.bkahlert.netmon.enrichment.HostServicesEnricher
import com.bkahlert.netmon.enrichment.LockdownProbe
import com.bkahlert.netmon.identity.AppleCodes
import com.bkahlert.netmon.identity.IdentityEnricher
import com.bkahlert.netmon.identity.IdentityResolver
import com.bkahlert.netmon.identity.LockdownClues
import com.bkahlert.netmon.identity.MdnsClues
import com.bkahlert.netmon.identity.OuiClues
import com.bkahlert.netmon.identity.RouterClues
import com.bkahlert.netmon.identity.SsdpClues
import com.bkahlert.netmon.logging.LoggingSettings
import com.bkahlert.netmon.mdns.JmDNS
import com.bkahlert.netmon.mdns.JmDNSServiceInfoCache
import com.bkahlert.netmon.model_identification.DeviceModelCodes
import com.bkahlert.netmon.model_identification.load
import com.bkahlert.netmon.model_identification.resource
import com.bkahlert.netmon.mqtt.MqttPublisher
import com.bkahlert.netmon.net.SystemInterfaceAddressResolver
import com.bkahlert.netmon.net.cidr
import com.bkahlert.netmon.net.isWireless
import com.bkahlert.netmon.net.network
import com.bkahlert.netmon.net.onePerNetwork
import com.bkahlert.netmon.net.networkInterface
import com.bkahlert.netmon.nmap.NmapMacPrefixesProvisioner
import com.bkahlert.netmon.nmap.NmapNetworkScanner
import com.bkahlert.netmon.nmap.NmapSettings
import com.bkahlert.netmon.router.FritzBoxEndpoint
import com.bkahlert.netmon.router.FritzBoxHosts
import com.bkahlert.netmon.router.FritzBoxSettings
import com.bkahlert.netmon.router.Tr064Client
import com.bkahlert.netmon.scanner.NetmonScanner
import com.bkahlert.netmon.scanner.NetworkFilterSettings
import com.bkahlert.netmon.scanner.ScannerSettings
import com.bkahlert.netmon.serialization.JsonFormat
import com.bkahlert.netmon.ssdp.DescriptionFetcher
import com.bkahlert.netmon.ssdp.SsdpCache
import java.net.InetAddress
import java.net.InterfaceAddress
import java.net.http.HttpClient
import java.time.Duration
import java.util.Collections
import kotlin.system.exitProcess

class Application(
    private val hostname: String = kotlin.runCatching { InetAddress.getLocalHost() }
        .getOrElse { throw IllegalStateException("Failed to determine localhost", it) }
        .hostName
        .also { check(it.isNotBlank()) { "Failed to determine hostname" } },
    private val interfaceAddresses: () -> Iterable<InterfaceAddress> = SystemInterfaceAddressResolver()::resolve,
) {

    private val cache: FileCache by lazy { FileCache.of("netmon") }
    private val nmapMacPrefixesProvisioner: NmapMacPrefixesProvisioner by lazy { NmapMacPrefixesProvisioner(cache) }
    private val nmapNetworkScanner by lazy {
        NmapNetworkScanner().apply {
            dataDir?.let(nmapMacPrefixesProvisioner::provisionIn)
        }
    }
    private val appleCodes by lazy { AppleCodes(DeviceModelCodes.load(DeviceModelCodes.resource)) }
    private val lockdownProbe by lazy { LockdownProbe() }

    /** One client for the router's TR-064 calls and the SSDP description fetches, which both speak plain HTTP/1.1 in the LAN. */
    private val lanHttp by lazy { HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(3)).build() }

    fun start() {
        logger.info("Configuration: {}", configuration(hostname, cache.toString()))

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
                FritzBoxSettings,
            ).joinToString(separator = "") { settings ->
                "\n${settings::class.simpleName.orEmpty().padStart(30)}: ${settings.toString().substringAfter('[').substringBeforeLast(']')}"
            },
        )

        val namedInterfaceAddresses: () -> Iterable<Pair<InterfaceAddress, String>> = {
            interfaceAddresses()
                .mapNotNull { interfaceAddress ->
                    interfaceAddress.networkInterface?.let { interfaceAddress to it.name }
                }
                // A board on Wi-Fi and on a cable in the same LAN scans it once, over the cable.
                .onePerNetwork(network = { (interfaceAddress, _) -> interfaceAddress.network }, wired = { (_, name) -> !isWireless(name) })
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
            logger.info("{} connected", it)
        }


        val serviceInfoCaches = Collections.synchronizedMap(mutableMapOf<InetAddress, JmDNSServiceInfoCache>())
        val scanners = Collections.synchronizedMap(mutableMapOf<InetAddress, NetmonScanner>())
        val ssdpCaches = Collections.synchronizedMap(mutableMapOf<InetAddress, SsdpCache>())
        val ssdpListeners = Collections.synchronizedMap(mutableMapOf<InetAddress, AutoCloseable>())
        val routerTables = Collections.synchronizedMap(mutableMapOf<InetAddress, FritzBoxHosts>())

        val application = SlicedApplication(
            slice = namedInterfaceAddresses,
            start = { (interfaceAddress, interfaceName) ->
                val serviceInfoCache = serviceInfoCaches.getOrPut(interfaceAddress.address) {
                    JmDNSServiceInfoCache(JmDNS(interfaceAddress.address, hostname), serviceTypes = emptyArray())
                }
                val ssdpCache = ssdpCaches.getOrPut(interfaceAddress.address) { SsdpCache(DescriptionFetcher(lanHttp)) }
                ssdpListeners.getOrPut(interfaceAddress.address) {
                    ssdpCache.listen(checkNotNull(interfaceAddress.networkInterface))
                }
                val routerTable = routerTables.getOrPut(interfaceAddress.address) {
                    FritzBoxHosts(
                        // Discovered per call inside the table's error handling, so a malformed URL is a warning, not a crash.
                        client = { FritzBoxEndpoint.discover(FritzBoxSettings.url, serviceInfoCache)?.let { Tr064Client(it, FritzBoxSettings.credentials, lanHttp) } },
                        credentials = FritzBoxSettings.credentials,
                    ).also { it.start() }
                }

                val topicSubstitutions = mapOf("node" to hostname, "interface" to interfaceName, "cidr" to interfaceAddress.cidr.toString())
                val scanTopic = ScanEventSettings.topic.toString(topicSubstitutions)
                val hostTopic = HostEventSettings.topic.toString(topicSubstitutions)

                scanners.getOrPut(interfaceAddress.address) {
                    NetmonScanner(
                        interfaceAddress = interfaceAddress,
                        scanner = nmapNetworkScanner,
                        enrichers = arrayOf(
                            IdentityEnricher(
                                IdentityResolver(),
                                sources = listOf(
                                    RouterClues(routerTable),
                                    MdnsClues(serviceInfoCache, appleCodes),
                                    SsdpClues(ssdpCache, serviceInfoCache),
                                    OuiClues(),
                                ),
                                fallbacks = listOf(LockdownClues(LockdownProbe.Lookup(lockdownProbe::model), appleCodes)),
                            ),
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
                scanners.remove(interfaceAddress.address)
                ssdpListeners.remove(interfaceAddress.address)?.close()
                ssdpCaches.remove(interfaceAddress.address)
                routerTables.remove(interfaceAddress.address)?.close()
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
        check(failed.isEmpty()) { "Errors occurred scanning the following ${failed.size} network(s): ${failed.joinToString { it.first.cidr.toString() }}" }
    }

    companion object {

        private val logger by SLF4J

        /** Returns the configuration block the start logs: the hostname, the cache, and the maximum heap the runtime allows in bytes. */
        fun configuration(hostname: String, cache: String): String = listOf(
            "hostname" to hostname,
            "cache" to cache,
            "max heap" to "${Runtime.getRuntime().maxMemory()} bytes",
        ).joinToString(separator = "") { (key, value) -> "\n${key.padStart(30)}: $value" }

        @JvmStatic
        fun main(args: Array<out String>) {
            try {
                LoggingSettings.apply(*args)
                logger.info("Application starting with pid={} and args={}", Pid.current.value, args.asList())
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
