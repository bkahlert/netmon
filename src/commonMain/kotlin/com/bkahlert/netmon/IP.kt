package com.bkahlert.netmon

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

expect class IP(value: String) : Comparable<IP> {
    val value: String
    val bytes: UByteArray
}

val IP.filenameString: String
    get() = value
        .replace('.', '-')
        .replace(':', '-')

object IPSerializer : KSerializer<IP> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("IP", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: IP) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): IP = IP(decoder.decodeString())
}
