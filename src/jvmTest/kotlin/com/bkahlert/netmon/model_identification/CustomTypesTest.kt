package com.bkahlert.netmon.model_identification

import com.bkahlert.kommons.test.createTempDirectory
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.inspectors.forAll
import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class CustomTypesTest {

    @Test
    fun description() = runTest {
        forAll(
            row("com.bkahlert.netmon.media-stick", "Media Stick"),
            row("com.bkahlert.netmon.speaker", "Speaker"),
            row("com.bkahlert.netmon.wireless-speaker", "Wireless Speaker"),
        ) { deviceModelCode, expected ->
            CustomTypes.description(deviceModelCode) shouldBe expected
        }
    }

    @Test
    fun all_icons() = runTest {
        val outputDir = createTempDirectory()
        CustomTypes.allIcons(outputDir) { icons -> icons.firstOrNull { it is Icon.Symbol } }
        CustomTypes.keys.forAll { identifier ->
            outputDir.resolve(identifier).shouldExist()
        }
    }
}
