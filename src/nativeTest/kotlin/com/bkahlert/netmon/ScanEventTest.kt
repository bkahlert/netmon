package com.bkahlert.netmon

import com.bkahlert.serialization.JsonFormat
import io.kotest.assertions.json.shouldEqualJson
import kotlinx.serialization.encodeToString
import kotlin.test.Test

class ScanEventTest {

    @Test
    fun to_json() {
        val event: ScanEvent = ScanEvent.HostDownEvent(Host("10.0.0.1"))
        JsonFormat.encodeToString(event) shouldEqualJson """
            {
              "event": "host-down",
              "host": {
                "ip": "10.0.0.1"
              }
            }
        """.trimIndent()
    }
}
