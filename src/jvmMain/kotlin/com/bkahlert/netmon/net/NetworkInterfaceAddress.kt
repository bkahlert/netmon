package com.bkahlert.netmon.net

import java.net.InterfaceAddress
import java.net.NetworkInterface

/** A [InterfaceAddress] and the [NetworkInterface] it belongs to. */
typealias NetworkInterfaceAddress = Pair<NetworkInterface, InterfaceAddress>

/** Returns all [NetworkInterfaceAddress] instances. */
fun NetworkInterfaceAddresses(): List<NetworkInterfaceAddress> =
    NetworkInterface.getNetworkInterfaces().toList().flatMap { networkInterface ->
        networkInterface.interfaceAddresses.map { interfaceAddress ->
            networkInterface to interfaceAddress
        }
    }
