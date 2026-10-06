package com.bkahlert.netmon

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.string.shouldMatch
import kotlin.test.Test

class ApplicationTest {

    @Test
    fun shutdown_stops_workers_before_closing_the_publisher() {
        val order = mutableListOf<String>()
        val resources = ApplicationResources(AutoCloseable { order += "publisher" })
        resources.ownWorkerManager { order += "workers" }

        resources.close()

        order shouldContainExactly listOf("workers", "publisher")
    }

    @Test
    fun configuration_names_the_max_heap_in_bytes() {
        val result = Application.configuration(hostname = "host", cache = "cache")

        result shouldMatch Regex("(?s).*max heap: \\d+ bytes.*")
    }
}
