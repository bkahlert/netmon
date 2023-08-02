package com.bkahlert.netmon

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Settings for the network monitor. */
data object Settings {

    /** The host name of the MQTT broker. */
    const val BROKER_HOST: String = "test.mosquitto.org"

    /** The topic name for scan events. */
    const val SCAN_TOPIC: String = "dt/netmon/+/scan"

    /** The topic name for host events. */
    const val HOST_TOPIC: String = "dt/netmon/+/host"

    /** The duration after which a host state is considered stable. */
    val STATE_CHANGE_STABLE_DURATION: Duration = 1.hours

    /** Settings for the network monitor's scanner. */
    object Scanner {
        /** The port of the MQTT broker used to publish events. */
        const val BROKER_PORT: Int = 1883

        /** The minimum number of hosts a network address's interface needs to cover to be used. */
        const val MIN_HOSTS: String = "2"

        /** The maximum number of hosts a network address's interface needs to cover to be used. */
        const val MAX_HOSTS: String = "16777216"

        /** Whether the host scanning process runs privileged. */
        const val PRIVILEGED_SCAN: Boolean = true
    }

    /** Settings for the network monitor's web UI. */
    object WebDisplay {
        /** The port of the MQTT broker used to subscribe to events. */
        const val BROKER_PORT: Int = 8081

        /** The interval in which the time-relevant information in the UI are updated. */
        val REFRESH_INTERVAL: Duration = 1.seconds

        /** The duration after which scans are no longer displayed. */
        val SCAN_OUTDATED_THRESHOLD: Duration = 5.minutes

        /** The duration for which changes are strongly highlighted. */
        val STATE_CHANGE_STRONG_HIGHLIGHT_DURATION: Duration = 10.seconds

        /** The duration for which changes are highlighted. */
        val STATE_CHANGE_HIGHLIGHT_DURATION: Duration = 60.seconds
    }
}
