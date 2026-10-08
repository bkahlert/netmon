package com.bkahlert.netmon.scanner.identity

import com.bkahlert.netmon.contract.Host
import com.bkahlert.netmon.contract.Kind
import com.bkahlert.netmon.contract.Link
import com.bkahlert.netmon.contract.LinkSpeed

/** Where a clue comes from; the resolver ranks sources per field. */
enum class Source {
    /** A value a person set, in the router's UI. */
    USER,

    /** A protocol record that describes the device itself: mDNS TXT, SSDP description. */
    PROTOCOL,

    /** An Apple model code the device advertises, accepted by [AppleCodes]. */
    APPLE_CODE,

    /** The mDNS host name. */
    MDNS_HOST,

    /** The name nmap's reverse lookup found. */
    DNS,

    /** A brand or product word in a name. */
    NAME_TOKEN,

    /** The router's host table, automatic fields. */
    ROUTER,

    /** The MAC prefix table. */
    OUI,
}

/** One claim about one field of a host. */
sealed interface Clue {
    val source: Source

    data class Name(val value: String, override val source: Source) : Clue
    data class Model(val value: String, override val source: Source) : Clue
    data class Vendor(val value: String, override val source: Source) : Clue
    data class DeviceKind(val kind: Kind, override val source: Source) : Clue
    data class Attachment(val link: Link, override val source: Source) : Clue
    data class Speed(val speed: LinkSpeed, override val source: Source) : Clue
    data class Mac(val value: String, override val source: Source) : Clue
}

/** Contributes clues about a host from a cache; never from the network. */
fun interface ClueSource {
    fun clues(host: Host): List<Clue>
}
