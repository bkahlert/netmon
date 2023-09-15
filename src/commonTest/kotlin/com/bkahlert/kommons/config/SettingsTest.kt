package com.bkahlert.kommons.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class SettingsTest {

    @Test
    fun no_user_value_provided() {
        withTestConfig {
            TestSettings() should {
                it.stringWithNoDefault shouldBe null
                it.stringWithDefault shouldBe "default"
                it.intWithNoDefault shouldBe null
                it.intWithDefault shouldBe 37
            }
        }
    }

    @Test
    fun user_value_provided() {
        withTestConfig("foo" to "42") {
            TestSettings() should {
                it.stringWithNoDefault shouldBe "42"
                it.stringWithDefault shouldBe "42"
                it.intWithNoDefault shouldBe 42
                it.intWithDefault shouldBe 42
            }
        }
    }

    @Test
    fun user_value_of_illegal_type() {
        withTestConfig("foo" to "forty-two") {
            TestSettings() should {
                shouldThrow<IllegalArgumentException> { it.intWithNoDefault }
                shouldThrow<IllegalArgumentException> { it.intWithDefault }
            }
        }
    }

    @Test
    fun derived_name() {
        withTestConfig("foo" to "42") {
            object : TestSettings() {
                val foo: String? by setting()
            } should {
                it.foo shouldBe "42"
            }
        }
    }

    @Test
    fun nested_derived_name() {
        withTestConfig("bar.baz" to "69") {
            object : TestSettings("bar") {
                val baz: String? by setting()
            } should {
                it.baz shouldBe "69"
            }
        }
    }
}

expect fun <R> withTestConfig(vararg config: Pair<String, String>, block: () -> R): R

open class TestSettings(name: String? = null) : Settings(name) {
    val stringWithNoDefault: String? by setting(name = "foo")
    val stringWithDefault: String by setting("default", name = "foo")
    val intWithNoDefault by setting<Int>(name = "foo")
    val intWithDefault by setting(37, name = "foo")
}
