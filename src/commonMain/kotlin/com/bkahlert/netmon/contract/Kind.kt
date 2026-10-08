package com.bkahlert.netmon.contract

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * The class of a device, in the FRITZ!Box's vocabulary plus [AIR_PURIFIER], [HUB], [LAPTOP] and [TELEVISION].
 *
 * Serialized as its [token]; an unknown token reads as [GENERIC], so a newer scanner never breaks an older display.
 */
@Serializable(with = KindSerializer::class)
enum class Kind(val token: String) {
    AIR_CONDITIONER("AirConditioner"),
    AIR_PURIFIER("AirPurifier"),
    BUTTON("Button"),
    CAMERA("Camera"),
    CIRCUIT_BOARD("CircuitBoard"),
    COMPUTER("Computer"),
    DOOR_BELL("DoorBell"),
    DOOR_LOCK("DoorLock"),
    GAMING_DEVICE("GamingDevice"),
    GENERIC("Generic"),
    HUB("Hub"),
    IP_PHONE("IPPhone"),
    LAMP("Lamp"),
    LAPTOP("Laptop"),
    MONITOR("Monitor"),
    NETWORK_SWITCH("NetworkSwitch"),
    PHONE("Phone"),
    PRINTER("Printer"),
    ROBOT("Robot"),
    ROUTER("Router"),
    SENSOR("Sensor"),
    SET_TOP_BOX("SetTopBox"),
    SHUTTER("Shutter"),
    SMART_WATCH("SmartWatch"),
    SMARTPHONE("Smartphone"),
    SOCKET("Socket"),
    SPEAKER("Speaker"),
    STORAGE("Storage"),
    TABLET("Tablet"),
    TELEVISION("Television"),
    THERMOSTAT("Thermostat");

    companion object {
        /** Returns the kind with the given [token], or [GENERIC] if none has it. */
        fun of(token: String): Kind = entries.firstOrNull { it.token == token } ?: GENERIC
    }
}

object KindSerializer : KSerializer<Kind> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Kind", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Kind) = encoder.encodeString(value.token)
    override fun deserialize(decoder: Decoder): Kind = Kind.of(decoder.decodeString())
}
