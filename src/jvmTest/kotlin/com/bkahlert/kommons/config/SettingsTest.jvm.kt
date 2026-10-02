package com.bkahlert.kommons.config

import java.util.Properties

actual fun <R> withTestConfig(vararg config: Pair<String, String>, block: () -> R): R {
    val oldConfig = System.getProperties()
    val testConfig = Properties().apply {
        putAll(oldConfig)
        putAll(config)
    }

    System.setProperties(testConfig)

    try {
        return block()
    } finally {
        System.setProperties(oldConfig)
    }
}
