package com.bkahlert.kommons.config

import com.bkahlert.netmon.serialization.JsonFormat
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import kotlinx.serialization.SerializationException
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
    fun unquoted_string_deserialization() {
        withTestConfig("foo" to "4:2") {
            object : Settings() {
                val setting: String? by setting(name = "foo", stringFormat = JsonFormat)
            } should { shouldThrow<SerializationException> { it.setting } }
            object : Settings() {
                val setting: String? by setting(name = "foo")
            } should { it.setting shouldBe "4:2" }
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

    @Test
    fun to_string() {
        withTestConfig("foo" to "42") {
            @Suppress("unused", "RegExpRedundantEscape")
            object : Settings() {
                val foo: String? by setting()
                val bar: String? by setting()
                val path: String? by setting()
            }.toString() shouldMatch Regex("Settings\\[(?:sys|uri):foo=42, default:bar=null, (?:env|default):path=.+\\]")
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
