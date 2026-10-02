package com.bkahlert.netmon.logging

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
    fun wildcard_patterns() = runTest {
        val wildcards = Debug(
            DebugMode(true, "*.netmon*"),
            DebugMode(false, "*mdns*"),
        )
        forAll(
            row("com.bkahlert.netmon.net", true),
            row("com.bkahlert.netmon.mdns.JmDNSServiceInfoCache", false),
            row("io.netty", null),
        ) { namespace, expected ->
            wildcards.state(namespace) shouldBe expected
        }
    }

    @Test
    fun apply() = runTest {
        val levels = mapOf(
            "netmon.test" to LogLevel.INFO,
            "foo.bar" to LogLevel.INFO,
            "foo.baz" to LogLevel.INFO,
        )

        debug.apply(levels) should {
            it["netmon.test"] shouldBe LogLevel.DEBUG
            it["foo.bar"] shouldBe LogLevel.OFF
            it["foo.baz"] shouldBe LogLevel.INFO
        }
    }
}
