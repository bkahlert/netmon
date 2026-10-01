package com.bkahlert.netmon.model_identification

import com.bkahlert.netmon.uri.toUri

@JsModule("./$TEST_DEVICE_CODE_MODES_RESOURCE_NAME")
@JsNonModule
private external val testDeviceCodeModesResource: String
actual suspend fun testDeviceModelCodesFromResource(): DeviceModelCodes =
    DeviceModelCodes.load(testDeviceCodeModesResource.toUri())
