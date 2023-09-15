package com.bkahlert.netmon.logging

import com.bkahlert.kommons.serialization.UnquotedStringsFormat.Companion.unquoted
import com.bkahlert.netmon.serialization.JsonFormat
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.serialization.decodeFromString
import kotlin.test.Test

class DebugSerializerTest {

    val jsonFormat = JsonFormat.unquoted

    @Test
    fun no_mode() {
        jsonFormat.decodeFromString<Debug>("").shouldBeEmpty()
    }

    @Test
    fun blank_mode() {
        jsonFormat.decodeFromString<Debug>(" ").shouldBeEmpty()
    }

    @Test
    fun one_mode() {
        jsonFormat.decodeFromString<Debug>("netmon:test").shouldContainExactly(
            DebugMode(true, "netmon:test"),
        )
    }

    @Test
    fun multiple_modes() {
        jsonFormat.decodeFromString<Debug>("netmon:test,-foo:bar").shouldContainExactly(
            DebugMode(true, "netmon:test"),
            DebugMode(false, "foo:bar"),
        )
    }

    @Test
    fun invalid_mode() {
        jsonFormat.decodeFromString<Debug>("netmon:test,,-,-foo:bar").shouldContainExactly(
            DebugMode(true, "netmon:test"),
            DebugMode(false, "foo:bar"),
        )
    }
}
