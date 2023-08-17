package com.bkahlert.netmon

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.math.BigInteger

/** Settings for the network monitor's scanner. */
object NetworkFilterSettings : Settings("network") {

    /** The minimum number of hosts a network address's interface needs to cover to be used. */
    val minHosts: BigInteger by setting(default = BigInteger("2"), deserializer = BigIntegerSerializer)

    /** The maximum number of hosts a network address's interface needs to cover to be used. */
    val maxHosts: BigInteger by setting(default = BigInteger("16777216"), deserializer = BigIntegerSerializer)

    val hostCountRange: ClosedRange<BigInteger> get() = minHosts..maxHosts
}

private object BigIntegerSerializer : KSerializer<BigInteger> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BigInteger", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): BigInteger = BigInteger(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: BigInteger) = encoder.encodeString(value.toString())
}
