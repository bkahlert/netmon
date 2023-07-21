package com.bkahlert.netmon

sealed interface ScanEvent {

    fun publish() {
        println(this)
    }

    data class ScanRestoredEvent(val result: ScanResult) : ScanEvent {
        override fun toString(): String = "${this::class.simpleName}(up=${result.hosts.size})"
    }

    data class ScanCompletedEvent(val result: ScanResult) : ScanEvent {
        override fun toString(): String = "${this::class.simpleName}(up=${result.hosts.size})"
    }

    data class HostDownEvent(val host: Host) : ScanEvent
    data class HostUpEvent(val host: Host) : ScanEvent
}
