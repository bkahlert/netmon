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
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.paths.shouldContainFiles
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.BeforeTest
import kotlin.test.Test

class TypesTest {

    @BeforeTest
    fun setUp() {
        Logback["com.bkahlert.netmon.exec"].level = Level.WARN
    }

    @Test
    fun keys() = runTest {
        TestTypes.keys.shouldContainExactly(
            setOf(
                "public.device",
                "public.computer",
                "com.example.device",
                "com.example.foo",
                "com.example.foo.tower",
                "com.example.foopro",
                "com.example.foopro-cylinder",
                "com.example.bar",
                "com.example.bar-x",
                "com.example.bar-x-1",
                "com.example.homebuttonless-bar",
                "com.example.homebuttonless-device",
                "com.example.bar",
            )
        )
    }

    @Test
    fun conforming_types() = runTest {
        TestTypes.conformingTypes("com.example.foopro-cylinder")
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
    fun conforming_types_all() = runTest {
        TestTypes.keys.forAll { type ->
            val conformingTypes = TestTypes.conformingTypes(type).toList()
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
            TestTypes.description(identifier) shouldBe expected
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
            TestTypes.icons(identifier) shouldBe expected
        }
    }

    @Test
    fun all_icons() = runTest {
        TestTypes.allIcons(createTempDirectory()) { it.firstOrNull { it is IconImageTemplate } } should {
            it.shouldContainFiles("com.example.foopro-cylinder", "com.example.bar-x-1")
            it.resolve("com.example.foopro-cylinder").shouldContainFiles("icon_16x16.png", "icon_[selected]32x32@2x.png")
            it.resolve("com.example.bar-x-1").shouldContainFiles("icon_16x16.png", "icon_[selected]32x32@2x.png")
            it.shouldNotBeNull()
        }
    }

    @Test
    fun plus() = runTest {
        forAll(
            row("com.example.foopro-cylinder", "Foo Pro"),
            row("com.example.bar-x-1", "Bar X (Model A1865, A1901, A1902, A1903)"),
            row("com.example.baz", "Baz"),
        ) { identifier, expected ->
            (TestTypes + CustomTestTypes).description(identifier) shouldBe expected
        }

        forAll(
            row(
                "com.example.foopro-cylinder",
                Icons(IconImage("com.example.foopro-cylinder.icns"), IconImageTemplate("SidebarFooProCylinder.icns"), Symbol("foopro.gen2")),
            ),
            row(
                "com.example.bar-x-1",
                Icons(IconImage("com.example.bar-x-1.icns")),
            ),
            row(
                "com.example.baz",
                Icons(IconImage("Baz.icns")),
            ),
        ) { identifier, expected ->
            (TestTypes + CustomTestTypes).icons(identifier) shouldBe expected
        }
    }

    companion object {
        val TestTypes = Types(
            declarations = listOf(
                UTExportedTypeDeclaration(
                    description = "Device",
                    icons = UTTypeIcons(iconFile = "GenericQuestionMarkIcon.icns"),
                    identifier = "public.device",
                ),
                UTExportedTypeDeclaration(
                    conformsTo = listOf("public.device"),
                    description = "Computer",
                    identifier = "public.computer",
                ),
                UTExportedTypeDeclaration(
                    conformsTo = listOf("public.device"),
                    description = "Example device",
                    identifier = "com.example.device",
                ),
                UTExportedTypeDeclaration(
                    conformsTo = listOf("public.computer", "com.example.device"),
                    description = "Foo",
                    icons = UTTypeIcons(iconFile = "com.example.led-cinema-display-27.icns"),
                    identifier = "com.example.foo",
                    tagSpecification = UTTypeTagSpecification(
                        deviceModelCodes = listOf("Foointosh", "Foo"),
                        osTypes = listOf("gfoo", "root"),
                    ),
                ),
                UTExportedTypeDeclaration(
                    conformsTo = listOf("com.example.foo"),
                    description = "Tower",
                    icons = UTTypeIcons(iconFile = "com.example.foopro-2019.icns"),
                    identifier = "com.example.foo.tower",
                    tagSpecification = UTTypeTagSpecification(
                        deviceModelCodes = listOf("Tower"),
                    ),
                ),
                UTExportedTypeDeclaration(
                    conformsTo = listOf("com.example.foo"),
                    description = "Foo Pro",
                    icons = UTTypeIcons(
                        iconFile = "com.example.foopro-2019.icns",
                        templateIconFile = "SidebarFooPro.icns",
                        symbolName = "foopro.gen3",
                    ),
                    identifier = "com.example.foopro",
                    tagSpecification = UTTypeTagSpecification(
                        deviceModelCodes = listOf("FooPro"),
                        osTypes = listOf("sbMP"),
                    ),
                ),
                UTExportedTypeDeclaration(
                    conformsTo = listOf("com.example.foopro", "com.example.foo.tower"),
                    icons = UTTypeIcons(
                        iconFile = "com.example.foopro-cylinder.icns",
                        templateIconFile = "SidebarFooProCylinder.icns",
                        symbolName = "foopro.gen2",
                        symbolHeroName = "foopro.gen2.fill",
                        symbolVariantNames = mapOf("fill" to "foopro.gen2.fill"),
                    ),
                    identifier = "com.example.foopro-cylinder",
                    tagSpecification = UTTypeTagSpecification(
                        deviceModelCodes = listOf("FooPro6,1"),
                        osTypes = listOf("sbMC"),
                    ),
                ),
                UTExportedTypeDeclaration(
                    conformsTo = listOf("com.example.ios-device"),
                    description = "Bar",
                    icons = UTTypeIcons(
                        templateIconFile = "SidebarBar.icns",
                        iconFile = "com.example.bar.icns",
                        symbolName = "bar.gen1",
                        symbolVariantNames = mapOf(
                            "apps" to "apps.bar",
                            "apps.badge_plus" to "apps.bar.badge.plus",
                            "arrow_forward" to "bar.and.arrow.forward",
                            "arrow_turnupforward" to "arrow.turn.up.forward.bar",
                            "badge_play" to "bar.gen1.badge.play",
                            "camerarear" to "bar.rear.camera",
                            "fill.arrow_turnupforward" to "arrow.turn.up.forward.bar.fill",
                            "landscape" to "bar.gen1.landscape",
                            "landscape.apps" to "apps.bar.landscape",
                            "landscape.apps.righttoleft" to "apps.bar.landscape.rtl",
                            "lock_locked" to "lock.bar",
                            "lock_unlocked" to "lock.open.bar",
                            "radio_leftright" to "bar.gen1.radiowaves.left.and.right",
                            "slash" to "bar.gen1.slash",
                        ),
                    ),
                    identifier = "com.example.bar",
                    tagSpecification = UTTypeTagSpecification(
                        deviceModelCodes = listOf("M68AP", "Bar1,1"),
                        osTypes = listOf("iphn", "sbPh"),
                    ),
                ),
                UTExportedTypeDeclaration(
                    conformsTo = listOf("com.example.homebuttonless-bar"),
                    description = "Bar X",
                    icons = UTTypeIcons(iconFile = "com.example.bar.icns"),
                    identifier = "com.example.bar-x",
                ),
                UTExportedTypeDeclaration(
                    conformsTo = listOf("com.example.bar-x"),
                    description = "Bar X (Model A1865, A1901, A1902, A1903)",
                    icons = UTTypeIcons(iconFile = "com.example.bar-x-1.icns"),
                    identifier = "com.example.bar-x-1",
                    tagSpecification = UTTypeTagSpecification(
                        deviceModelCodes = listOf("D22AP", "D221AP", "Bar10,3", "Bar10,6", "Bar"),
                    ),
                ),
                UTExportedTypeDeclaration(
                    conformsTo = listOf("com.example.bar", "com.example.homebuttonless-device"),
                    icons = UTTypeIcons(
                        templateIconFile = "SidebarBar.icns",
                        iconFile = "com.example.bar.icns",
                        symbolName = "bar.gen2",
                        symbolVariantNames = mapOf(
                            "apps" to "apps.bar",
                            "apps.badge_plus" to "apps.bar.badge.plus",
                            "arrow_forward" to "bar.and.arrow.forward",
                            "arrow_turnupforward" to "arrow.turn.up.forward.bar",
                            "badge_play" to "bar.gen2.badge.play",
                            "camerarear" to "bar.rear.camera",
                            "fill.arrow_turnupforward" to "arrow.turn.up.forward.bar.fill",
                            "landscape" to "bar.gen2.landscape",
                            "landscape.apps" to "apps.bar.landscape",
                            "landscape.apps.righttoleft" to "apps.bar.landscape.rtl",
                            "lock_locked" to "lock.bar",
                            "lock_unlocked" to "lock.open.bar",
                            "radio_leftright" to "bar.gen2.radiowaves.left.and.right",
                            "slash" to "bar.gen2.slash",
                        ),
                    ),
                    identifier = "com.example.homebuttonless-bar",
                ),
                UTExportedTypeDeclaration(
                    identifier = "com.example.homebuttonless-device",
                ),
                UTExportedTypeDeclaration(
                    conformsTo = listOf("public.device"),
                    description = "Bar",
                    icons = UTTypeIcons(
                        iconFile = "com.example.bar.icns",
                        symbolName = "bar.gen1",
                        symbolVariantNames = mapOf(
                            "apps" to "apps.bar",
                            "apps.badge_plus" to "apps.bar.badge.plus",
                            "arrow_forward" to "bar.and.arrow.forward",
                            "arrow_turnupforward" to "arrow.turn.up.forward.bar",
                            "badge_play" to "bar.gen1.badge.play",
                            "camerarear" to "bar.rear.camera",
                            "fill.arrow_turnupforward" to "arrow.turn.up.forward.bar.fill",
                            "landscape" to "bar.gen1.landscape",
                            "landscape.apps" to "apps.bar.landscape",
                            "landscape.apps.righttoleft" to "apps.bar.landscape.rtl",
                            "lock_locked" to "lock.bar",
                            "lock_unlocked" to "lock.open.bar",
                            "radio_leftright" to "bar.gen1.radiowaves.left.and.right",
                            "slash" to "bar.gen1.slash",
                        )
                    ),
                    identifier = "com.example.bar",
                    tagSpecification = UTTypeTagSpecification(
                        deviceModelCodes = listOf("M68AP", "Bar1,1"),
                    ),
                ),
            ),
            iconResolver = Bundle(BundleTest.TEST_BUNDLE_DIR)::iconPath,
        )

        val CustomTestTypes = Types(
            UTExportedTypeDeclaration(
                conformsTo = listOf("com.example.baz"),
                description = "Baz",
                icons = UTTypeIcons(iconFile = "Baz.icns"),
                identifier = "com.example.baz",
                tagSpecification = UTTypeTagSpecification(deviceModelCodes = listOf("Baz")),
            ),
            iconResolver = { BundleTest.TEST_BUNDLES_DIR.resolve("../icons").resolve(it).takeIf(Path::exists) },
        )
    }
}
