package com.bkahlert.netmon

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class LogMessageTest {

    @Test
    fun parses_the_simple_loggers_bracketed_lines() {
        val result = LogMessage.parse("[INFO] com.bkahlert.netmon.Application - Settings: foo")

        result shouldBe LogMessage(LogMessage.Level.INFO, "Settings: foo")
    }

    @Test
    fun a_continuation_line_is_not_a_message() {
        LogMessage.parse("                      hostname: netmon").shouldBeNull()
    }
}
