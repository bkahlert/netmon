package com.bkahlert.netmon.model_identification

actual suspend fun testDeviceModelCodesFromResource(): DeviceModelCodes =
    DeviceModelCodes.load(
        DeviceModelCodes::class.java.classLoader.getResource(TEST_DEVICE_CODE_MODES_RESOURCE_NAME)
            ?: error("Resource $TEST_DEVICE_CODE_MODES_RESOURCE_NAME not found."),
    )
