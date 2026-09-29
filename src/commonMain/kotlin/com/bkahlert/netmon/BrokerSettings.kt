package com.bkahlert.netmon

import com.bkahlert.kommons.config.Settings

object BrokerSettings : Settings("broker") {

    /** The host name of the MQTT broker; defaults to [defaultBrokerHost]. */
    val host: String by setting(default = defaultBrokerHost)

    /** The port of the MQTT broker used to publish events. */
    val port: Int by setting(default = 8080)
}

/**
 * The broker host used when none is configured: in the browser the host the page was loaded from
 * (the display is served by the same board as the broker), on the JVM the public test broker.
 */
internal expect val defaultBrokerHost: String
