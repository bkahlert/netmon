package com.bkahlert.netmon

import io.kotest.matchers.string.shouldMatch
import kotlin.test.Test

class ApplicationTest {

    @Test
    fun configuration_names_the_max_heap_in_bytes() {
        val result = Application.configuration(hostname = "host", cache = "cache")

        result shouldMatch Regex("(?s).*max heap: \\d+ bytes.*")
    }
}
