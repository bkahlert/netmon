package com.bkahlert.netmon.net

import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.logging.logback.StructuredArguments
import com.bkahlert.netmon.Settings
import java.math.BigInteger
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InterfaceAddress
import java.net.NetworkInterface

// TODO: pass filters as parameters
object InterfaceFilter {
    private val logger by SLF4J

    /**
     * Filters the given [networkInterfaces] by a specified set of criteria, and
     * return the [NetworkInterface]s and their [InterfaceAddress]es that match.
     */
    fun filter(
        hostCountRange: ClosedRange<BigInteger> = BigInteger(Settings.minHosts)..BigInteger(Settings.maxHosts),
        networkInterfaces: List<NetworkInterface> = NetworkInterface.getNetworkInterfaces().toList(),
    ): Map<NetworkInterface, List<InterfaceAddress>> = networkInterfaces
        .filter { iface ->
            iface.isUp.also {
                if (!it) logger.info("Skipping {} because it's down.", StructuredArguments.kv("interface", iface))
            }
        }
        .filterNot { iface ->
            iface.isLoopback.also {
                if (it) logger.info("Skipping {} because it's a loopback interface.", StructuredArguments.kv("interface", iface))
            }
        }
        .associateWith { iface: NetworkInterface ->
            val ifaceAddresses = iface.interfaceAddresses.toList()
            logger.debug("Checking {} of {}", StructuredArguments.o("addresses", ifaceAddresses), StructuredArguments.kv("interface", iface))
            ifaceAddresses
                .filter { ifaceAddress ->
                    when (val inetAddress = ifaceAddress.address) {
                        is Inet4Address -> {
                            inetAddress.isSiteLocalAddress.also {
                                if (!it) logger.debug("Skipping {} because it's not site-local.", StructuredArguments.kv("interfaceAddress", ifaceAddress))
                            }
                        }

                        is Inet6Address -> {
                            inetAddress.isLinkLocalAddress.also {
                                if (!it) logger.debug("Skipping {} because it's not link-local.", StructuredArguments.kv("interfaceAddress", ifaceAddress))
                            }
                        }

                        else -> {
                            logger.debug("Skipping {} because it's no supported IP protocol.", StructuredArguments.kv("interfaceAddress", ifaceAddress))
                            false
                        }
                    }
                }
                .filter { ifaceAddress ->
                    (ifaceAddress.maxHosts in hostCountRange).also {
                        if (!it) logger.debug(
                            "Skipping {} because it's {} is not in {}",
                            StructuredArguments.kv("interfaceAddress", ifaceAddress),
                            StructuredArguments.kv("max-host-count", ifaceAddress.maxHosts),
                            StructuredArguments.kv("allowed-host-count-range", hostCountRange),
                        )
                    }
                }
                .also {
                    if (it.isEmpty()) logger.info("Skipping {} because no eligible interface address was found.", StructuredArguments.kv("interface", iface))
                }
        }
        .filterValues { it.isNotEmpty() }
}
