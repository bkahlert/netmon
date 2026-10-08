package com.bkahlert.netmon.scanner.support.logging

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = DebugSerializer::class)
class Debug(val debugModes: List<DebugMode>) : Collection<DebugMode> by debugModes {
    constructor(vararg debugModes: DebugMode) : this(debugModes.asList())

    fun state(namespace: String): Boolean? = debugModes.reversed().firstNotNullOfOrNull { debugMode ->
        when (debugMode.matches(namespace)) {
            true -> debugMode.enabled
            else -> null
        }
    }

    fun apply(levels: Map<String, LogLevel>): Map<String, LogLevel> = levels.mapValues { (logger, level) ->
        val lowerCaseName = logger.lowercase()
        when (state(lowerCaseName) ?: state(lowerCaseName.replace('.', ':'))) {
            true -> LogLevel.DEBUG
            false -> LogLevel.OFF
            null -> level
        }
    }

    override fun toString(): String = "${Debug::class.simpleName}(${debugModes.joinToString(",")})"
}

// TODO support wildcards, see https://www.npmjs.com/package/debug
data class DebugMode(
    val enabled: Boolean,
    val pattern: String,
) {
    val regex: Regex = pattern.split('*').joinToString(".*") { Regex.escape(it) }.toRegex()
    fun matches(namespace: String): Boolean = regex.matches(namespace)
}

internal data object DebugSerializer : KSerializer<Debug> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Debug", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Debug) {
        encoder.encodeString(value.debugModes.joinToString(",") { if (it.enabled) it.pattern else "-${it.pattern}" })
    }

    override fun deserialize(decoder: Decoder): Debug = Debug(
        decoder.decodeString().split(" ", ",")
            .filterNot { it == "" }.filterNot { it == "-" }
            .map { DebugMode(enabled = !it.startsWith('-'), pattern = it.removePrefix("-")) }
    )
}
