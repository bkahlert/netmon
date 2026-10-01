package com.bkahlert.netmon.model_identification

import ch.qos.logback.classic.Level
import com.bkahlert.netmon.logging.Logback
import com.bkahlert.kommons.test.createTempDirectory
import com.bkahlert.netmon.model_identification.Icon.IconImage
import com.bkahlert.netmon.model_identification.Icon.IconImageTemplate
import com.bkahlert.netmon.model_identification.Icon.Symbol
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forNone
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.paths.shouldContainFiles
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class BundleTypesTest {

    companion object {
        val TEST_BUNDLE_TYPES: BundleTypes = BundleTypes(Bundle(BundleTest.TEST_BUNDLE_DIR))
    }

    @BeforeTest
    fun setUp() {
        Logback["com.bkahlert.kommons.exec"].level = Level.WARN
    }

    @Test
    fun all_types() = runTest {
        TEST_BUNDLE_TYPES shouldHaveSize 12
    }

    @Test
    fun conforming_types() = runTest {
        TEST_BUNDLE_TYPES.conformingTypes("com.example.foopro-cylinder")
            .map { it.identifier }
            .toList()
            .shouldContainExactly(
                "com.example.foopro-cylinder",
                "com.example.foopro",
                "com.example.foo.tower",
                "com.example.foo",
                "public.computer",
                "com.example.device",
                "public.device",
            )
    }

    @Test
    fun conforming_types_across_bundles() = runTest {
        TEST_BUNDLE_TYPES.bundle.exportedTypeDeclarations.shouldNotBeNull().forNone {
            it.identifier shouldBe "com.example.homebuttonless-device"
        }
        TEST_BUNDLE_TYPES.conformingTypes("com.example.bar-x-1")
            .map { it.identifier }
            .toList()
            .shouldContainExactly(
                "com.example.bar-x-1",
                "com.example.bar-x",
                "com.example.homebuttonless-bar",
                "com.example.bar",
                "com.example.homebuttonless-device",
                "public.device",
            )
    }

    @Test
    fun conforming_types_all() = runTest {
        TEST_BUNDLE_TYPES.keys.forAll { type ->
            val conformingTypes = TEST_BUNDLE_TYPES.conformingTypes(type).toList()
            conformingTypes.shouldNotBeEmpty()
        }
    }

    @Test
    fun description() = runTest {
        forAll(
            row("com.example.foopro-cylinder", "Foo Pro"),
            row("com.example.bar-x-1", "Bar X (Model A1865, A1901, A1902, A1903)"),
            row("com.example.baz", null),
        ) { identifier, expected ->
            TEST_BUNDLE_TYPES.description(identifier) shouldBe expected
        }
    }

    @Test
    fun icons() = runTest {
        forAll(
            row(
                "com.example.foopro-cylinder",
                Icons(IconImage("com.example.foopro-cylinder.icns"), IconImageTemplate("SidebarFooProCylinder.icns"), Symbol("foopro.gen2"))
            ),
            row(
                "com.example.bar-x-1",
                Icons(IconImage("com.example.bar-x-1.icns")),
            ),
            row("com.example.baz", null),
        ) { identifier, expected ->
            TEST_BUNDLE_TYPES.icons(identifier) shouldBe expected
        }
    }

    @Test
    fun all_icons() = runTest {
        TEST_BUNDLE_TYPES.allIcons(createTempDirectory()) { it.firstOrNull { it is IconImageTemplate } } should {
            it.shouldContainFiles("com.example.foopro-cylinder", "com.example.bar-x-1")
            it.resolve("com.example.foopro-cylinder").shouldContainFiles("icon_16x16.png", "icon_[selected]32x32@2x.png")
            it.resolve("com.example.bar-x-1").shouldContainFiles("icon_16x16.png", "icon_[selected]32x32@2x.png")
            it.shouldNotBeNull()
        }
    }

    @Test
    fun system() = runTest {
        BundleTypes.CoreTypes should { coreTypes ->
            coreTypes.shouldNotBeNull()
            coreTypes.size shouldBeGreaterThan 100
            coreTypes.forAll {
                coreTypes.conformingTypes(it.key).shouldNotBeNull()
            }
        }
    }
}
