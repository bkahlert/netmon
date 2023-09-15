package com.bkahlert.kommons.config

import ch.qos.logback.classic.Level
import com.bkahlert.kommons.logging.logback.Logback
import com.bkahlert.netmon.logging.get
import java.util.Properties

actual fun <R> withTestConfig(vararg config: Pair<String, String>, block: () -> R): R {
    val oldConfig = System.getProperties()
    val testConfig = Properties().apply {
        putAll(oldConfig)
        putAll(config)
    }

    val logger = Logback[Settings::class.java.`package`.name]
    val oldLogLevel = logger.level

    System.setProperties(testConfig)
    logger.level = Level.DEBUG

    try {
        return block()
    } finally {
        logger.level = oldLogLevel
        System.setProperties(oldConfig)
    }
}
