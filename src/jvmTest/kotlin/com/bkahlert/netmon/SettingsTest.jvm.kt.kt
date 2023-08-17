package com.bkahlert.netmon

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlin.test.Test

actual class SettingsTest {

    @Test
    actual fun no_user_value_provided() {
        System.clearProperty("foo")
        TestSettings() should {
            it.stringWithNoDefault shouldBe null
            it.stringWithDefault shouldBe "default"
            it.intWithNoDefault shouldBe null
            it.intWithDefault shouldBe 37
        }
    }

    @Test
    actual fun user_value_provided() {
        System.setProperty("foo", "42")
        TestSettings() should {
            it.stringWithNoDefault shouldBe "42"
            it.stringWithDefault shouldBe "42"
            it.intWithNoDefault shouldBe 42
            it.intWithDefault shouldBe 42
        }
    }

    @Test
    actual fun user_value_of_illegal_type() {
        System.setProperty("foo", "forty-two")
        TestSettings() should {
            shouldThrow<IllegalArgumentException> { it.intWithNoDefault }
            shouldThrow<IllegalArgumentException> { it.intWithDefault }
        }
    }

    @Test
    actual fun derived_name() {
        System.setProperty("foo", "42")
        object : TestSettings() {
            val foo: String? by setting()
        } should {
            it.foo shouldBe "42"
        }
    }

    @Test
    actual fun nested_derived_name() {
        System.setProperty("bar.baz", "69")
        object : TestSettings("bar") {
            val baz: String? by setting()
        } should {
            it.baz shouldBe "69"
        }
    }
}
