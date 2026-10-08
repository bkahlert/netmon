package com.bkahlert.netmon.scanner.app

import com.bkahlert.netmon.support.config.Settings
import com.bkahlert.netmon.support.serialization.UnquotedStringsFormat.Companion.unquoted
import com.bkahlert.netmon.contract.serialization.JsonFormat

object BrokerSettings : Settings("broker", JsonFormat.unquoted) {

    /** The host name of the MQTT broker; defaults to the public test broker. */
    val host: String by setting(default = "test.mosquitto.org")

    /** The port of the MQTT broker used to publish events. */
    val port: Int by setting(default = 8080)
}
