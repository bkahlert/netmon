package com.bkahlert.netmon

object BrokerSettings : Settings("broker") {

    /** The host name of the MQTT broker. */
    val host: String by setting(default = "test.mosquitto.org")

    /** The port of the MQTT broker used to publish events. */
    val port: Int? by setting()
}
