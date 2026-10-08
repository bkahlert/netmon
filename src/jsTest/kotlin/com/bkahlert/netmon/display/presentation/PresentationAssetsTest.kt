package com.bkahlert.netmon.display.presentation

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class PresentationAssetsTest {

    @Test
    fun model_codes_are_fetchable() = runTest {
        DeviceModelCodes.load(DeviceModelCodes.resource).shouldNotBeEmpty()
    }

    @Test
    fun device_icons_are_fetchable() = runTest {
        DeviceIcons.load(DeviceIcons.resource).symbol("mdi:devices").shouldNotBeNull()
    }
}
