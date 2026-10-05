package com.bkahlert.netmon.model_identification

import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Ignore
import kotlin.test.Test

class DeviceModelCodesTest {

    @Test
    fun codes() {
        TEST_DEVICE_MODEL_CODES.shouldContainExactlyInAnyOrder("FooPro6,1", "Bar10,6", "Baz1,1")
    }

    @Test
    fun description() = runTest {
        forAll(
            row("FooPro6,1", "Foo Pro"),
            row("Bar10,6", "Bar X (Model A1865, A1901, A1902, A1903)"),
            row("Baz1,1", null),
            row("Qux", null),
        ) { deviceModelCode, expected ->
            TEST_DEVICE_MODEL_CODES.description(deviceModelCode) shouldBe expected
        }
    }

    @Test
    fun symbol() = runTest {
        forAll(
            row("FooPro6,1", FOOPRO_SVG),
            row("Bar10,6", BAR_SVG),
            row("Baz1,1", null),
            row("Qux", null),
        ) { deviceModelCode, expected ->
            TEST_DEVICE_MODEL_CODES.symbol(deviceModelCode) shouldBe expected
        }
    }

    @Test
    fun symbolName() = runTest {
        forAll(
            row("FooPro6,1", "foopro.gen3"),
            row("Baz1,1", "private.name"),
            row("Qux", null),
        ) { deviceModelCode, expected ->
            TEST_DEVICE_MODEL_CODES.symbolName(deviceModelCode) shouldBe expected
        }
    }

    @Test
    fun serialization() {
        val serialized = Json.encodeToString(TEST_DEVICE_MODEL_CODES)
        val deserialized = Json.decodeFromString<DeviceModelCodes>(serialized)
        deserialized shouldBe TEST_DEVICE_MODEL_CODES
    }

    /*
     resource / module handling with Kotlin/JS/MPP is just a nightmare
     Leads to, although the resource should be there:
     IllegalStateException: Failed to fetch http://localhost:9876/test-device-model-codes.json: Not Found
     */
    @Ignore
    @Test
    fun from_resource() = runTest {
        testDeviceModelCodesFromResource() shouldBe TEST_DEVICE_MODEL_CODES
    }

    companion object {
        const val FOOPRO_SVG: String =
            """<svg xmlns="http://www.w3.org/2000/svg" data-symbol-name="foopro.gen3" viewBox="0 0 10 10"><path fill="currentColor" d="M0 0L10 10Z"/></svg>"""
        const val BAR_SVG: String =
            """<svg xmlns="http://www.w3.org/2000/svg" data-symbol-name="bar.x" viewBox="0 0 10 10"><path fill="currentColor" fill-opacity="0.5" d="M0 10L10 0Z"/></svg>"""
        val TEST_DEVICE_MODEL_CODES: DeviceModelCodes = DeviceModelCodes(
            models = mapOf(
                "FooPro6,1" to DeviceModelCodes.Model(description = "Foo Pro", symbol = "foopro.gen3"),
                "Bar10,6" to DeviceModelCodes.Model(description = "Bar X (Model A1865, A1901, A1902, A1903)", symbol = "bar.x"),
                "Baz1,1" to DeviceModelCodes.Model(description = null, symbol = "private.name"),
            ),
            symbols = mapOf(
                "foopro.gen3" to FOOPRO_SVG,
                "bar.x" to BAR_SVG,
            ),
        )
    }
}

const val TEST_DEVICE_CODE_MODES_RESOURCE_NAME: String = "assets/test-device-model-codes.json"
expect suspend fun testDeviceModelCodesFromResource(): DeviceModelCodes
