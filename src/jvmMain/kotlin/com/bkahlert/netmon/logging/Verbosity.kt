package com.bkahlert.netmon.logging

enum class Verbosity(
    val levels: Map<String, LogLevel>
) {
    ERRORS_AND_WARNINGS(
        "root" to LogLevel.WARN,
        "javax.jmdns.impl.DNSIncoming" to LogLevel.ERROR, // Suppresses "There was an OPT answer. Not currently handled. Option code: 10"
        "com.bkahlert.netmon.Application" to LogLevel.INFO,
        "com.bkahlert.netmon.enrichment" to LogLevel.ERROR,
    ),
    VERBOSE(
        "root" to LogLevel.INFO,
        "javax.jmdns.impl.DNSIncoming" to LogLevel.ERROR, // Suppresses "There was an OPT answer. Not currently handled. Option code: 10"
        "com.bkahlert.netmon.exec" to LogLevel.WARN,
        "com.bkahlert.netmon.net" to LogLevel.WARN,
        "com.bkahlert.netmon.mdns.JmDNSServiceInfoCache" to LogLevel.WARN,
        "com.bkahlert.netmon.enrichment" to LogLevel.WARN,
        "com.bkahlert.netmon.mqtt" to LogLevel.WARN,
    ),
    VERY_VERBOSE(
        "root" to LogLevel.INFO,
        "javax.jmdns.impl.DNSIncoming" to LogLevel.ERROR, // Suppresses "There was an OPT answer. Not currently handled. Option code: 10"
        "com.bkahlert.netmon.Application" to LogLevel.DEBUG,
        "com.bkahlert.netmon.mdns" to LogLevel.DEBUG,
        "com.bkahlert.netmon.mdns.JmDNSServiceInfoCache" to LogLevel.INFO,
    ),
    EXTREMELY_VERBOSE(
        "root" to LogLevel.DEBUG,
        "org.eclipse.paho" to LogLevel.INFO,
        "javax.jmdns" to LogLevel.INFO,
    ),
    ;

    constructor(vararg levels: Pair<String, LogLevel>) : this(levels.toMap())

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
