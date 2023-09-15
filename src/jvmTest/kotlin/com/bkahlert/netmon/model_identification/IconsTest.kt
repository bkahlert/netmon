package com.bkahlert.netmon.model_identification

import com.bkahlert.kommons.test.createTempDirectory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.paths.shouldBeADirectory
import io.kotest.matchers.paths.shouldBeAFile
import io.kotest.matchers.paths.shouldContainFiles
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.test.Test

class IconsTest {

    @Test
    fun icon_files() = runTest {
        Icons.of(UTExportedTypeDeclaration.PublicItemType).shouldContainExactly(
            Icon.IconFiles(
                name = "generic",
                variants = listOf("20x20", "20x20@2x", "145x145", "145x145@2x"),
                extension = "png",
            ),
        )
    }

    @Test
    fun icons() = runTest {
        Icons.of(UTExportedTypeDeclaration.PublicDataType).shouldContainExactly(
            Icon.IconImage("GenericDocumentIcon.icns"),
            Icon.IconImageTemplate("SidebarGenericFile.icns"),
            Icon.Symbol("doc"),
        )
    }

    @Test
    fun to_iconset() = runTest {
        val assets = createTempDirectory()
        Icon.IconImage("SidebarBar.icns").toIconSet(assets, CoreTypesTest.CORE_TYPES.bundle.allBundles) should {
            it.shouldBeADirectory()
            it.shouldContainFiles("icon_16x16.png", "icon_[selected]32x32@2x.png")
        }
        Icon.Symbol("doc").toIconSet(assets, CoreTypesTest.CORE_TYPES.bundle.allBundles) should {
            it.shouldBeAFile()
            it.fileName.pathString shouldBe "doc.svg"
        }
    }

    @Test
    fun invalid_to_iconset() = runTest {
        val assets = createTempDirectory()
        forAll(
            row(Icon.IconFiles("foo", listOf("bar"), "baz")),
            row(Icon.IconImage("foo", "icns")),
            row(Icon.IconImageTemplate("SidebarFoo", "icns")),
            row(Icon.Symbol("foo.bar")),
        ) { icon ->
            shouldThrow<IllegalArgumentException> { icon.toIconSet(assets, CoreTypesTest.CORE_TYPES.bundle.allBundles) }
        }
        assets.listDirectoryEntries().shouldBeEmpty()
    }
}
