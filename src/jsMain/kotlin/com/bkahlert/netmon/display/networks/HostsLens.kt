package com.bkahlert.netmon.display.networks

import com.bkahlert.netmon.contract.Event.ScanEvent
import com.bkahlert.netmon.contract.Host
import dev.fritz2.core.Lens
import com.bkahlert.netmon.contract.Event

private object HostsLens : Lens<ScanEvent, List<Host>> {
    override val id: String = "hosts"
    override fun get(parent: ScanEvent): List<Host> = parent.hosts
    override fun set(parent: ScanEvent, value: List<Host>): ScanEvent = parent.copy(hosts = value)
}

fun ScanEvent.Companion.hosts(): Lens<ScanEvent, List<Host>> = HostsLens
