package com.bkahlert.netmon

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = StatusSerializer::class)
sealed interface Status {

    data object UP : Status
    data object DOWN : Status
    data class UNKNOWN(val value: String) : Status

    companion object {
        fun of(value: String): Status = when (value.uppercase()) {
            "UP" -> UP
            "DOWN" -> DOWN
            else -> UNKNOWN(value)
        }
    }
}

data object StatusSerializer : KSerializer<Status> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Status", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Status) {
        when (value) {
            Status.UP -> encoder.encodeString("up")
            Status.DOWN -> encoder.encodeString("down")
            is Status.UNKNOWN -> encoder.encodeString("unknown-${value.value}")
        }
    }

    override fun deserialize(decoder: Decoder): Status =
        when (val string = decoder.decodeString().lowercase()) {
            "up" -> Status.UP
            "down" -> Status.DOWN
            else -> Status.UNKNOWN(string.removePrefix("unknown-"))
        }
}
