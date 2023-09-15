package com.bkahlert.netmon.logging

import ch.qos.logback.classic.Level
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class DebugTest {

    val debug = Debug(
        DebugMode(true, "netmon:test"),
        DebugMode(false, "foo:bar"),
    )

    @Test
    fun state() = runTest {
        forAll(
            row("netmon:test", true),
            row("foo:bar", false),
            row("foo:baz", null),
        ) { namespace, expected ->
            debug.state(namespace) shouldBe expected
        }
    }

    @Test
    fun apply() = runTest {
        val levels = mapOf(
            "netmon.test" to Level.INFO,
            "foo.bar" to Level.INFO,
            "foo.baz" to Level.INFO,
        )

        debug.apply(levels) should {
            it["netmon.test"] shouldBe Level.DEBUG
            it["foo.bar"] shouldBe Level.OFF
            it["foo.baz"] shouldBe Level.INFO
        }
    }
}
