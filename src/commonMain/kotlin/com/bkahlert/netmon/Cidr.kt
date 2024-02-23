package com.bkahlert.netmon

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// TODO rename to Network

@Serializable(with = NetworkSerializer::class)
data class Cidr(
    val ip: IP,
    val mask: Int,
) {
    val filenameString: String get() = "${ip.filenameString}_$mask"

    init {
        val range = 0..ip.bytes.size.times(Byte.SIZE_BITS)
        require(mask in range) { "Invalid mask $mask is not in range $range" }
    }

    private val text by lazy { "$ip/$mask" }

    override fun toString(): String = text

    companion object {

        val PATTERN = Regex("""(?<ip>[^/]+)/(?<mask>\d+)""")

        fun parse(text: String): Cidr {
            val (ipPart, maskPart) = PATTERN.matchEntire(text)?.destructured ?: throw IllegalArgumentException("Invalid CIDR: $text")
            val ip = IP.of(ipPart)
            val mask = maskPart.toInt()
            return Cidr(
                ip = ip,
                mask = if (ip is IPv4 && ipPart.contains(':')) {
                    IPv4.SIZE_BITS - (IPv6.SIZE_BITS - mask)
                } else {
                    mask
                },
            )
        }
    }
}

data object NetworkSerializer : KSerializer<Cidr> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("com.bkahlert.netmon.NetworkSerializer", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Cidr) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Cidr = Cidr.parse(decoder.decodeString())
}
