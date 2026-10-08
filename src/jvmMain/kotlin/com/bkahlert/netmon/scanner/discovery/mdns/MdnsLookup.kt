package com.bkahlert.netmon.scanner.discovery.mdns

import com.bkahlert.netmon.contract.IP

/** What the identity rules need from the mDNS cache. */
interface MdnsLookup {
    /** Returns the host names that resolve to [ip], or `null` if none is known. */
    fun servers(ip: IP): Set<String>?

    /** Returns the services announced at [ip], or `null` if none is known. */
    fun services(ip: IP): Set<ServiceInfo>?

    /** Returns every known service of the given [application], for example `tr064`. */
    fun services(application: String): List<ServiceInfo>
}
