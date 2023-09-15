package com.bkahlert.netmon.model_identification

import com.bkahlert.netmon.model_identification.DeviceModelCodesLookup.Image
import com.bkahlert.netmon.serialization.DataUrl
import com.bkahlert.netmon.serialization.DataUrlTest
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Ignore
import kotlin.test.Test

class DeviceModelCodesTest {

    @Test
    fun description() = runTest {
        forAll(
            row("FooPro6,1", "Foo Pro"),
            row("Bar10,6", "Bar X (Model A1865, A1901, A1902, A1903)"),
            row("Baz", null),
        ) { deviceModelCode, expected ->
            TEST_DEVICE_MODEL_CODES.description(deviceModelCode) shouldBe expected
            TEST_DEVICE_MODEL_CODES_WITH_EMBEDDED_ICONS.description(deviceModelCode) shouldBe expected
        }
    }

    @Test
    fun icon() = runTest {
        forAll(
            row(
                "FooPro6,1",
                Image("com.example.foopro-cylinder.icns", "com.example.foopro-cylinder.icns"),
                Image("com.example.foopro-cylinder.icns", DataUrlTest.URL_ENCODED_DATA_URL),
            ),
            row(
                "Bar10,6",
                Image("com.example.bar-x-1.icns", "com.example.bar-x-1.icns"),
                Image("com.example.bar-x-1.icns", DataUrlTest.BASE64_ENCODED_DATA_URL),
            ),
            row(
                "Baz",
                null,
                null,
            ),
        ) { deviceModelCode, expectedImageReference, expectedEmbeddedImage ->
            TEST_DEVICE_MODEL_CODES.icon(deviceModelCode) shouldBe expectedImageReference
            TEST_DEVICE_MODEL_CODES_WITH_EMBEDDED_ICONS.icon(deviceModelCode) shouldBe expectedEmbeddedImage
        }
    }

    @Test
    fun default_icon() = runTest {
        val deviceModelCodesWithDefaultIcon = TEST_DEVICE_MODEL_CODES
            .copy(identifierIconMappings = mapOf(null to "default.svg"))
        val deviceModelCodesWithEmbeddedDefaultIcon = deviceModelCodesWithDefaultIcon
            .copy(embeddedIcons = mapOf("default.svg" to DataUrl(DataUrlTest.BASE64_ENCODED_DATA_URL)))

        deviceModelCodesWithDefaultIcon.icon("Baz") shouldBe Image("default.svg", "default.svg")
        deviceModelCodesWithEmbeddedDefaultIcon.icon("Baz") shouldBe Image("default.svg", DataUrlTest.BASE64_ENCODED_DATA_URL)
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
        val TEST_DEVICE_MODEL_CODES_WITH_EMBEDDED_ICONS: DeviceModelCodes = DeviceModelCodes(
            deviceModelCodeToIdentifier = mapOf(
                "FooPro6,1" to "com.example.foopro-cylinder",
                "Bar10,6" to "com.example.bar-x-1",
            ),
            identifierDescriptionMappings = mapOf(
                "com.example.foopro-cylinder" to "Foo Pro",
                "com.example.bar-x-1" to "Bar X (Model A1865, A1901, A1902, A1903)",
            ),
            identifierIconMappings = mapOf(
                "com.example.foopro-cylinder" to "com.example.foopro-cylinder.icns",
                "com.example.bar-x-1" to "com.example.bar-x-1.icns",
            ),
            embeddedIcons = mapOf(
                "com.example.foopro-cylinder.icns" to DataUrl(DataUrlTest.URL_ENCODED_DATA_URL),
                "com.example.bar-x-1.icns" to DataUrl(DataUrlTest.BASE64_ENCODED_DATA_URL),
            ),
        )
        val TEST_DEVICE_MODEL_CODES: DeviceModelCodes = TEST_DEVICE_MODEL_CODES_WITH_EMBEDDED_ICONS.copy(embeddedIcons = null)
    }
}

const val TEST_DEVICE_CODE_MODES_RESOURCE_NAME: String = "assets/test-device-model-codes.json"
expect suspend fun testDeviceModelCodesFromResource(): DeviceModelCodes
