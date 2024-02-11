package com.bkahlert.netmon.logging

import ch.qos.logback.classic.Level

enum class Verbosity(
    val levels: Map<String, Level>
) {
    ERRORS_AND_WARNINGS(
        "root" to Level.WARN,
        "javax.jmdns.impl.DNSIncoming" to Level.ERROR, // Suppresses "There was an OPT answer. Not currently handled. Option code: 10"
        "com.bkahlert.netmon.Application" to Level.INFO,
        "com.bkahlert.netmon.enrichment" to Level.ERROR,
    ),
    VERBOSE(
        "root" to Level.INFO,
        "javax.jmdns.impl.DNSIncoming" to Level.ERROR, // Suppresses "There was an OPT answer. Not currently handled. Option code: 10"
        "com.bkahlert.kommons.cache" to Level.WARN,
        "com.bkahlert.kommons.exec" to Level.WARN,
        "com.bkahlert.netmon.net" to Level.WARN,
        "com.bkahlert.netmon.mdns.JmDNSServiceInfoCache" to Level.WARN,
        "com.bkahlert.netmon.enrichment" to Level.WARN,
        "com.bkahlert.netmon.mqtt" to Level.WARN,
    ),
    VERY_VERBOSE(
        "root" to Level.INFO,
        "javax.jmdns.impl.DNSIncoming" to Level.ERROR, // Suppresses "There was an OPT answer. Not currently handled. Option code: 10"
        "com.bkahlert.netmon.Application" to Level.DEBUG,
        "com.bkahlert.netmon.mdns" to Level.DEBUG,
        "com.bkahlert.netmon.mdns.JmDNSServiceInfoCache" to Level.INFO,
    ),
    EXTREMELY_VERBOSE(
        "root" to Level.DEBUG,
        "io.netty" to Level.INFO,
        "javax.jmdns" to Level.INFO,
    ),
    ;

    constructor(vararg levels: Pair<String, Level>) : this(levels.toMap())

    companion object {
        fun from(verbosity: Int): Verbosity = when (verbosity) {
            0 -> ERRORS_AND_WARNINGS
            1 -> VERBOSE
            2 -> VERY_VERBOSE
            else -> EXTREMELY_VERBOSE
        }

        fun from(vararg args: String): Verbosity =
            from(args.mapNotNull { it.takeIf { it.startsWith("-v") } }.sumOf { it.length - 1 })
    }
}
