package com.bkahlert.netmon.display.presentation

import com.bkahlert.netmon.display.presentation.uri.toUri

@JsModule("./$TEST_DEVICE_CODE_MODES_RESOURCE_NAME")
@JsNonModule
private external val testDeviceCodeModesResource: String
internal suspend fun testDeviceModelCodesFromResource(): DeviceModelCodes =
    DeviceModelCodes.load(testDeviceCodeModesResource.toUri())
