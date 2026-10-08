package com.bkahlert.netmon.scanner.app

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.io.IOException
import kotlin.test.Test

class NetworkResourcesTest {

    @Test
    fun close_releases_resources_in_reverse_order_once() {
        val order = mutableListOf<String>()
        val resources = NetworkResources()
        resources.own(AutoCloseable { order += "first" })
        resources.own(AutoCloseable { order += "second" })

        resources.close()
        resources.close()

        order shouldContainExactly listOf("second", "first")
    }

    @Test
    fun close_continues_after_failures_and_suppresses_later_failures() {
        val order = mutableListOf<String>()
        val firstFailure = IOException("first failure")
        val secondFailure = IOException("second failure")
        val resources = NetworkResources()
        resources.own(AutoCloseable { order += "first"; throw firstFailure })
        resources.own(AutoCloseable { order += "middle" })
        resources.own(AutoCloseable { order += "last"; throw secondFailure })

        val failure = shouldThrow<IOException> { resources.close() }

        failure shouldBe secondFailure
        failure.suppressed.toList() shouldContainExactly listOf(firstFailure)
        order shouldContainExactly listOf("last", "middle", "first")
    }
}
