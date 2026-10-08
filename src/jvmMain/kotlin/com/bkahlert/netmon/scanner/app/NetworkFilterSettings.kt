package com.bkahlert.netmon.scanner.app

import com.bkahlert.netmon.support.config.Settings
import com.bkahlert.netmon.support.serialization.UnquotedStringsFormat.Companion.unquoted
import com.bkahlert.netmon.contract.serialization.JsonFormat

/** Settings for the network monitor's scanner. */
object NetworkFilterSettings : Settings("network", JsonFormat.unquoted) {

    /**
     * The minimum number of host bits, that is, the IP address length minus the prefix.
     *
     * The default is `4`, which corresponds to IPv4 networks with a `/28` prefix and IPv6 networks, with a `/124` prefix.
     * Or differently put: networks with 2^4 = 16 IP addresses.
     */
    val minHostBits: UInt by setting(default = 4u)

    /**
     * The maximum number of host bits, that is, the IP address length minus the prefix.
     *
     * The default is `16`, which corresponds to IPv4 networks with a `/16` prefix and IPv6 networks, with a `/112` prefix.
     * Or differently put: networks with 2^16 = 65536 IP addresses.
     */
    val maxHostBits: UInt by setting(default = 16u)

    val hostBitsRange: IntRange get() = minHostBits.toInt()..maxHostBits.toInt()
}
