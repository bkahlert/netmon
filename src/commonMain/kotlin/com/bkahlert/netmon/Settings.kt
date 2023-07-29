package com.bkahlert.netmon

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
    }
}
