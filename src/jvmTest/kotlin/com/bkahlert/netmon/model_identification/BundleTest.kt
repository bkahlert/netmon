package com.bkahlert.netmon.model_identification

import ch.qos.logback.classic.Level
import com.bkahlert.kommons.io.toPath
import com.bkahlert.netmon.logging.Logback
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.nio.file.Paths
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.test.BeforeTest
import kotlin.test.Test

class BundleTest {

    companion object {
        val TEST_BUNDLES_DIR = checkNotNull(BundleTest::class.java.classLoader.getResource("bundles")) { "Failed to find test bundles" }.toPath()
        val TEST_BUNDLE_DIR = TEST_BUNDLES_DIR / "CoreTypes.bundle"
    }

    @BeforeTest
    fun setUp() {
        Logback["com.bkahlert.kommons.exec"].level = Level.WARN
    }

    @Test
    fun find() = runTest {
        Bundle.find(TEST_BUNDLES_DIR) shouldHaveSize 1
        Bundle.find(TEST_BUNDLE_DIR / "Contents") shouldHaveSize 1
    }

    @Test
    fun info() = runTest {
        Bundle(TEST_BUNDLE_DIR) should {
            it.packageType shouldBe Bundle.PackageType.BNDL
            it.identifier shouldBe "com.example.coretypes"
            it.name shouldBe "CoreTypes"
            it.version shouldBe "123"
            it.toString() shouldBe """
                Bundle(packageType=BNDL, identifier=com.example.coretypes, name=CoreTypes, version=123, path=${it.path})
            """.trimIndent()
        }
    }

    @Test
    fun all_bundles() = runTest {
        Bundle(TEST_BUNDLE_DIR).allBundles should {
            it shouldHaveAtLeastSize 2
            it.map(Bundle::path).shouldContainExactly(
                TEST_BUNDLE_DIR,
                TEST_BUNDLE_DIR / "Contents" / "Library" / "MobileDevices.bundle",
            )
        }
    }

    @Test
    fun system() = runTest {
        val coreServicesDirectory = Paths.get("/System/Library/CoreServices")
        if (coreServicesDirectory.exists()) {
            val bundles = Bundle.find(coreServicesDirectory) shouldHaveAtLeastSize 50
            bundles.forAll { shouldNotThrowAny { it.toString() } }
        }
    }
}
