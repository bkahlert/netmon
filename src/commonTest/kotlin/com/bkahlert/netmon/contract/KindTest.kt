package com.bkahlert.netmon.contract

import com.bkahlert.netmon.contract.serialization.JsonFormat
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlin.test.Test

class KindTest {

    @Test
    fun every_kind_round_trips_through_its_token() {
        Kind.entries.forEach { kind ->
            val json = JsonFormat.encodeToString(Kind.serializer(), kind)

            json shouldBe "\"${kind.token}\""
            JsonFormat.decodeFromString(Kind.serializer(), json) shouldBe kind
        }
    }

    @Test
    fun an_unknown_token_reads_as_generic() {
        val result = JsonFormat.decodeFromString(Kind.serializer(), "\"Hoverboard\"")

        result shouldBe Kind.GENERIC
    }

    @Test
    fun the_vocabulary_is_the_routers_plus_four() {
        Kind.entries.map { it.token } shouldBe listOf(
            "AirConditioner", "AirPurifier", "Button", "Camera", "CircuitBoard", "Computer", "DoorBell", "DoorLock",
            "GamingDevice", "Generic", "Hub", "IPPhone", "Lamp", "Laptop", "Monitor", "NetworkSwitch", "Phone", "Printer",
            "Robot", "Router", "Sensor", "SetTopBox", "Shutter", "SmartWatch", "Smartphone", "Socket", "Speaker", "Storage",
            "Tablet", "Television", "Thermostat",
        )
    }
}
