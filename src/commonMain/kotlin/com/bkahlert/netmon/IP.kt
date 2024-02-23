package com.bkahlert.netmon

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = IPSerializer::class)
expect sealed interface IP : Comparable<IP> {
    val bytes: ByteArray

    companion object {
        fun of(bytes: ByteArray): IP
        fun of(text: String): IP
    }
}

expect class IPv4(bytes: ByteArray) : IP {
    companion object {
        val SIZE_BITS: Int
        val SIZE_BYTES: Int
    }
}

expect class IPv6(bytes: ByteArray) : IP {
    companion object {
        val SIZE_BITS: Int
        val SIZE_BYTES: Int
    }
}

data object IPSerializer : KSerializer<IP> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("IP", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: IP) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): IP = IP.of(decoder.decodeString())
}

internal data object IPv6Compressor {

    private val zeroSequencesPattern: Regex = "(^0+|:0+)+:?".toRegex()
    private fun findLongestSequenceOfZeroHextets(ipv6: String): MatchResult? = zeroSequencesPattern
        .findAll(ipv6)
        .maxByOrNull { result ->
            result.range.let { it.last - it.first }
        }

    fun compress(ipv6: String): String = findLongestSequenceOfZeroHextets(ipv6)
        ?.let { ipv6.replaceRange(it.range, "::") }
        ?: ipv6
}

val IP.filenameString: String
    get() = toString()
        .replace('.', '-')
        .replace(':', '-')
