package com.bkahlert.netmon.logging

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class SLF4JTest {

    @Test
    fun member_logger_is_named_after_the_class() {
        Subject().logger.name shouldBe Subject::class.java.name
    }

    @Test
    fun companion_logger_is_named_after_the_enclosing_class() {
        SubjectWithCompanion.logger.name shouldBe SubjectWithCompanion::class.java.name
    }

    @Test
    fun top_level_logger_is_named_after_the_file_class() {
        topLevelLogger.name shouldBe "com.bkahlert.netmon.logging.SLF4JTestKt"
    }
}

private class Subject {
    val logger by SLF4J
}

private class SubjectWithCompanion {
    companion object {
        val logger by SLF4J
    }
}

private val topLevelLogger by SLF4J
