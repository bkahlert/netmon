package com.bkahlert.netmon

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data object Settings {
    val minHosts = "2"
    val maxHosts = "16777216"
    val privileged: Boolean = true

    val brokerHost: String = "test.mosquitto.org"

    object Scanner {
        val brokerPort: Int = 1883
    }

    object WebDisplay {
        val brokerPort: Int = 8081
        val removeScanEventsOlderThan: Duration = 5.minutes
        val strongHighlightHostChangesFor: Duration = 10.seconds
        val highlightHostChangesFor: Duration = 60.seconds
    }
}
