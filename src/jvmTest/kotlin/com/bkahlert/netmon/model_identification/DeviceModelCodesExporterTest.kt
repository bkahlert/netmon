package com.bkahlert.netmon.model_identification

import ch.qos.logback.classic.Level
import com.bkahlert.kommons.FileCache
import com.bkahlert.netmon.logging.Logback
import com.bkahlert.kommons.test.createTempDirectory
import com.bkahlert.netmon.model_identification.DeviceModelCodesLookup.Image
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.io.path.fileSize
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class DeviceModelCodesExporterTest {

    @BeforeTest
    fun setUp() {
        Logback["com.bkahlert.netmon.exec"].level = Level.WARN
        Logback[FileCache::class].level = Level.WARN
        Logback["com.bkahlert.kommons"].level = Level.DEBUG
    }

    @Test
    fun export_to() = runTest(timeout = 2.minutes) {
        val assets = createTempDirectory("assets")
        val deviceModelCodes = DeviceModelCodesExporter.exportTo(TypesTest.TestTypes, assets)
        deviceModelCodes.description("FooPro6,1") shouldBe "Foo Pro"
        deviceModelCodes.icon("FooPro6,1") shouldBe Image("SidebarFooProCylinder.png", assets.resolve("SidebarFooProCylinder.png").toDataUrl().url)
        deviceModelCodes.description("Bar10,6") shouldBe "Bar X (Model A1865, A1901, A1902, A1903)"
        deviceModelCodes.icon("Bar10,6") shouldBe Image("SidebarBar.png", assets.resolve("SidebarBar.png").toDataUrl().url)
        deviceModelCodes.description("Baz").shouldBeNull()
        deviceModelCodes.icon("Baz").shouldBeNull()

        val jsonExportWithExternalAssets = assets.resolve("device-model-codes.external-assets.json")
        val jsonExportWithEmbeddedAssets = assets.resolve("device-model-codes.embedded-assets.json")
        jsonExportWithExternalAssets.fileSize() shouldBeLessThan jsonExportWithEmbeddedAssets.fileSize()
        DeviceModelCodes.load(jsonExportWithExternalAssets) shouldHaveSize 12
        DeviceModelCodes.load(jsonExportWithEmbeddedAssets) shouldHaveSize 12

        val htmlExportWithExternalAssets = assets.resolve("device-model-codes.external-assets.html")
        val htmlExportWithEmbeddedAssets = assets.resolve("device-model-codes.embedded-assets.html")
        htmlExportWithExternalAssets.fileSize() shouldBeLessThan htmlExportWithEmbeddedAssets.fileSize()
    }
}
