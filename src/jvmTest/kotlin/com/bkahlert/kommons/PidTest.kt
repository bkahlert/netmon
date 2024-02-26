package com.bkahlert.kommons

import io.kotest.matchers.comparables.shouldBeGreaterThan
import kotlin.test.Test

class PidTest {

    @Test
    fun current() {
        Pid.current.value shouldBeGreaterThan 0
    }
}
