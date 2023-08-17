package com.bkahlert.netmon

import com.bkahlert.kommons.uri.Uri
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlin.test.Test

actual class SettingsTest {

    @Test
    actual fun no_user_value_provided() {
        UriSource.testUri = Uri("https://example.com")
        TestSettings() should {
            it.stringWithNoDefault shouldBe null
            it.stringWithDefault shouldBe "default"
            it.intWithNoDefault shouldBe null
            it.intWithDefault shouldBe 37
        }
    }

    @Test
    actual fun user_value_provided() {
        UriSource.testUri = Uri("https://example.com/?foo=42")
        TestSettings() should {
            it.stringWithNoDefault shouldBe "42"
            it.stringWithDefault shouldBe "42"
            it.intWithNoDefault shouldBe 42
            it.intWithDefault shouldBe 42
        }
    }

    @Test
    actual fun user_value_of_illegal_type() {
        UriSource.testUri = Uri("https://example.com/?foo=forty-two")
        TestSettings() should {
            shouldThrow<IllegalArgumentException> { it.intWithNoDefault }
            shouldThrow<IllegalArgumentException> { it.intWithDefault }
        }
    }

    @Test
    actual fun derived_name() {
        UriSource.testUri = Uri("https://example.com/?foo=42")
        object : TestSettings() {
            val foo: String? by setting()
        } should {
            it.foo shouldBe "42"
        }
    }

    @Test
    actual fun nested_derived_name() {
        UriSource.testUri = Uri("https://example.com/?bar.baz=69")
        object : TestSettings("bar") {
            val baz: String? by setting()
        } should {
            it.baz shouldBe "69"
        }
    }
}
