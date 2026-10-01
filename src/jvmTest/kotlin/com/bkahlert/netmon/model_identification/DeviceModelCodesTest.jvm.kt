package com.bkahlert.netmon.model_identification

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.should
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test

class DeviceModelCodesTestJvm {

    @Test
    fun resource() = runTest {
        DeviceModelCodes.resource.readText() should { resourceContents ->
            resourceContents.shouldNotBeBlank()
            Json.decodeFromString<DeviceModelCodes>(resourceContents).shouldNotBeEmpty()
        }
    }
}

actual suspend fun testDeviceModelCodesFromResource(): DeviceModelCodes =
    DeviceModelCodes.load(
        DeviceModelCodes::class.java.classLoader.getResource(TEST_DEVICE_CODE_MODES_RESOURCE_NAME)
            ?: error("Resource $TEST_DEVICE_CODE_MODES_RESOURCE_NAME not found."),
    )
