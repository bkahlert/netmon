package com.bkahlert.netmon

import com.bkahlert.netmon.Event.ScanEvent
import dev.fritz2.core.Lens

private object HostsLens : Lens<ScanEvent, List<Host>> {
    override val id: String = "hosts"
    override fun get(parent: ScanEvent): List<Host> = parent.hosts
    override fun set(parent: ScanEvent, value: List<Host>): ScanEvent = parent.copy(hosts = value)
}

fun ScanEvent.Companion.hosts(): Lens<ScanEvent, List<Host>> = HostsLens
