package com.bkahlert.netmon.scanner.app

import com.bkahlert.netmon.scanner.support.cache.FileCache
import com.bkahlert.netmon.scanner.support.cache.SystemLocations
import com.bkahlert.netmon.scanner.support.process.Pid
import com.bkahlert.netmon.scanner.support.logging.SLF4J
import com.bkahlert.netmon.scanner.scan.enrichment.HostServicesEnricher
import com.bkahlert.netmon.scanner.discovery.lockdown.LockdownProbe
import com.bkahlert.netmon.scanner.identity.AppleCodes
import com.bkahlert.netmon.scanner.identity.IdentityEnricher
import com.bkahlert.netmon.scanner.identity.LockdownClues
import com.bkahlert.netmon.scanner.identity.MdnsClues
import com.bkahlert.netmon.scanner.identity.OuiClues
import com.bkahlert.netmon.scanner.identity.RouterClues
import com.bkahlert.netmon.scanner.identity.SsdpClues
import com.bkahlert.netmon.scanner.identity.loadModelCatalog
import com.bkahlert.netmon.scanner.support.logging.LoggingSettings
import com.bkahlert.netmon.scanner.discovery.mdns.JmDNS
import com.bkahlert.netmon.scanner.discovery.mdns.JmDNSServiceInfoCache
import com.bkahlert.netmon.scanner.mqtt.MqttPublisher
import com.bkahlert.netmon.scanner.mqtt.ScannerEventPublisher
import com.bkahlert.netmon.scanner.support.net.SystemInterfaceAddressResolver
import com.bkahlert.netmon.scanner.support.net.cidr
import com.bkahlert.netmon.scanner.support.net.isWireless
import com.bkahlert.netmon.scanner.support.net.network
import com.bkahlert.netmon.scanner.support.net.onePerNetwork
import com.bkahlert.netmon.scanner.support.net.networkInterface
import com.bkahlert.netmon.scanner.nmap.NmapMacPrefixesProvisioner
import com.bkahlert.netmon.scanner.nmap.NmapNetworkScanner
import com.bkahlert.netmon.scanner.nmap.NmapScanAdapter
import com.bkahlert.netmon.scanner.nmap.NmapSettings
import com.bkahlert.netmon.scanner.discovery.router.FritzBoxEndpoint
import com.bkahlert.netmon.scanner.discovery.router.FritzBoxHosts
import com.bkahlert.netmon.scanner.discovery.router.FritzBoxSettings
import com.bkahlert.netmon.scanner.discovery.router.Tr064Client
import com.bkahlert.netmon.scanner.state.JsonScanStateStore
import com.bkahlert.netmon.scanner.scan.NetmonScanner
import com.bkahlert.netmon.scanner.scan.NetworkContext
import com.bkahlert.netmon.contract.serialization.JsonFormat
import com.bkahlert.netmon.contract.EventSource
import com.bkahlert.netmon.contract.ScanTopics
import com.bkahlert.netmon.scanner.discovery.ssdp.DescriptionFetcher
import com.bkahlert.netmon.scanner.discovery.ssdp.SsdpCache
import java.net.InetAddress
import java.net.InterfaceAddress
import java.net.http.HttpClient
import java.nio.file.Paths
import java.time.Duration
import kotlin.system.exitProcess
import kotlin.time.Clock
import com.bkahlert.netmon.contract.Event

class Application(
    private val hostname: String = kotlin.runCatching { InetAddress.getLocalHost() }
        .getOrElse { throw IllegalStateException("Failed to determine localhost", it) }
        .hostName
        .also { check(it.isNotBlank()) { "Failed to determine hostname" } },
    private val interfaceAddresses: () -> Iterable<InterfaceAddress> = SystemInterfaceAddressResolver(
        SystemInterfaceAddressResolver.NetworkInterfaceUpPredicate,
        SystemInterfaceAddressResolver.NetworkInterfaceNonLoopbackPredicate,
        SystemInterfaceAddressResolver.SiteOrLinkLocalIpAddressPredicate,
        SystemInterfaceAddressResolver.HostCountPredicate(NetworkFilterSettings.hostBitsRange),
    )::resolve,
) {

    private val cache: FileCache by lazy { FileCache(SystemLocations.NetmonCache) }
    private val nmapMacPrefixesProvisioner: NmapMacPrefixesProvisioner by lazy { NmapMacPrefixesProvisioner(cache) }
    private val nmapNetworkScanner by lazy {
        NmapNetworkScanner().apply {
            dataDir?.let(nmapMacPrefixesProvisioner::provisionIn)
        }
    }
    private val appleCodes by lazy { AppleCodes(loadModelCatalog()) }
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
        val resources = ApplicationResources(publisher)
        val shutdownHook = Thread(resources::close, "netmon-shutdown")
        var shutdownHookRegistered = false
        try {
            Runtime.getRuntime().addShutdownHook(shutdownHook)
            shutdownHookRegistered = true

            val application = SlicedApplication(
                slice = namedInterfaceAddresses,
                open = { (interfaceAddress, interfaceName) ->
                    val session = NetworkSession.open { sessionResources ->
                        val jmDns = sessionResources.own(JmDNS(interfaceAddress.address, hostname))
                        val serviceInfoCache = sessionResources.own(JmDNSServiceInfoCache(jmDns, serviceTypes = emptyArray()))
                        val ssdpCache = SsdpCache(DescriptionFetcher(lanHttp))
                        sessionResources.own(ssdpCache.listen(checkNotNull(interfaceAddress.networkInterface)))
                        val routerTable = sessionResources.own(
                            FritzBoxHosts(
                                // Discovered per call inside the table's error handling, so a malformed URL is a warning, not a crash.
                                client = { FritzBoxEndpoint.discover(FritzBoxSettings.url, serviceInfoCache)?.let { Tr064Client(it, FritzBoxSettings.credentials, lanHttp) } },
                                credentials = FritzBoxSettings.credentials,
                            ),
                        ).also { it.start() }

                        val eventSource = EventSource(hostname, interfaceName, interfaceAddress.cidr)
                        val scanTopic = ScanTopics.topic(ScanEventSettings.topic, eventSource)
                        val hostTopic = ScanTopics.topic(HostEventSettings.topic, eventSource)
                        val networkContext = NetworkContext(interfaceName, interfaceAddress.cidr)
                        val eventPublisher = ScannerEventPublisher(publisher, scanTopic, hostTopic)

                        NetmonScanner(
                            context = networkContext,
                            scanner = NmapScanAdapter(nmapNetworkScanner::scan),
                            enrichers = listOf(
                                IdentityEnricher(
                                    routerClues = RouterClues(routerTable),
                                    mdnsClues = MdnsClues(serviceInfoCache, appleCodes),
                                    ssdpClues = SsdpClues(ssdpCache, serviceInfoCache),
                                    ouiClues = OuiClues(),
                                    lockdownClues = LockdownClues(LockdownProbe.Lookup(lockdownProbe::model), appleCodes),
                                ),
                                HostServicesEnricher(serviceInfoCache),
                            ),
                            state = JsonScanStateStore(
                                Paths.get("scan.${networkContext.interfaceName}.${networkContext.cidr.filenameString}.json"),
                            ),
                            clock = Clock.System,
                            downAfter = ScannerSettings.downAfter,
                            onScan = eventPublisher::publishScan,
                            onChange = eventPublisher::publishChange,
                        )
                    }

                    object : SliceWorker by session {
                        override fun process() {
                            session.process()
                            Thread.sleep(ScannerSettings.pauseDuration.inWholeMilliseconds)
                        }

                        override fun close() {
                            session.close()
                            logger.info("Stopped scanning {}:{} and corresponding cache", interfaceName, interfaceAddress.cidr)
                        }
                    }
                },
            )

            val started = application.start()
            resources.ownWorkerManager { started.terminate() }
            val failed = started.waitForTermination().failed
            check(failed.isEmpty()) { "Errors occurred scanning the following ${failed.size} network(s): ${failed.joinToString { it.first.cidr.toString() }}" }
        } finally {
            try {
                resources.close()
            } finally {
                if (shutdownHookRegistered) {
                    try {
                        Runtime.getRuntime().removeShutdownHook(shutdownHook)
                    } catch (e: IllegalStateException) {
                        logger.debug("JVM shutdown is in progress; leaving the shutdown hook registered")
                    }
                }
            }
        }
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
