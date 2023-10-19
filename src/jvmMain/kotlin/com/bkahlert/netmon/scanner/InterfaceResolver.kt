package com.bkahlert.netmon.scanner

import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.logging.logback.StructuredArguments.kv
import com.bkahlert.kommons.logging.logback.StructuredArguments.o
import java.math.BigInteger
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InterfaceAddress
import java.net.NetworkInterface

class InterfaceResolver(
    val candidates: () -> List<InterfaceAddress> = {
        NetworkInterface.getNetworkInterfaces().toList().flatMap { it.interfaceAddresses.toList() }
    },
    vararg val predicates: Predicate = DefaultPredicates,
) {

    fun resolve(): List<InterfaceAddress> = candidates()
        .also { logger.info("Evaluating candidate {}", o<InterfaceAddress>(it)) }
        .let {
            predicates.fold(it) { acc, predicate ->
                val (passed, failed) = acc.partition(predicate)
                if (failed.isNotEmpty()) {
                    logger.info(
                        "{} {} not {}: {}",
                        failed.size,
                        if (failed.size == 1) "interface" else "interfaces",
                        predicate.description,
                        o<InterfaceAddress>(failed),
                    )
                }
                passed
            }
        }

    companion object {
        private val logger by SLF4J

        val InterfaceAddress.networkInterface: NetworkInterface?
            get() = runCatching { NetworkInterface.getByInetAddress(address) }
                .onFailure { logger.warn("Could not get network interface for {}", kv("interfaceAddress", this)) }
                .getOrNull()

        val NetworkInterfaceUpPredicate = Predicate("having network interface in up state") {
            it.networkInterface?.isUp == true
        }

        val NetworkInterfaceNonLoopbackPredicate = Predicate("having non-loopback network interface") {
            it.networkInterface?.isLoopback == false
        }

        val SiteOrLinkLocalIpAddressPredicate = Predicate("having site-local IPv4 or link-local IPv6 address") { interfaceAddress ->
            when (val inetAddress = interfaceAddress.address) {
                is Inet4Address -> inetAddress.isSiteLocalAddress
                is Inet6Address -> inetAddress.isLinkLocalAddress
                else -> false
            }
        }

        fun HostCountPredicate(
            hostBitsRange: IntRange
        ): Predicate {
            val hostCountRange = hostBitsRange.let {
                BigInteger.valueOf(2).pow(it.first)..BigInteger.valueOf(2).pow(it.last)
            }
            return Predicate("having host count in range $hostCountRange") { it.hostBits in hostBitsRange }
        }

        val DefaultPredicates = arrayOf(
            NetworkInterfaceUpPredicate,
            NetworkInterfaceNonLoopbackPredicate,
            SiteOrLinkLocalIpAddressPredicate,
            HostCountPredicate(NetworkFilterSettings.hostBitsRange),
        )
    }
}

class Predicate(
    val description: String,
    predicate: (InterfaceAddress) -> Boolean
) : (InterfaceAddress) -> Boolean by predicate {
    override fun toString(): String = description
}
