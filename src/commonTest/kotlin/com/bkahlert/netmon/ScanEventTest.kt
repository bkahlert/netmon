package com.bkahlert.netmon

import com.bkahlert.serialization.JsonFormat
import io.kotest.assertions.json.shouldEqualJson
import kotlinx.serialization.encodeToString
import kotlin.test.Test

class ScanEventTest {

    @Test
    fun to_json() {
        JsonFormat.encodeToString(ScanEvent.DOWN) shouldEqualJson ScanEvent.DOWN_STRING
    }
}

val ScanEvent.Companion.DOWN: ScanEvent get() = ScanEvent.HostDownEvent(Host("10.0.0.1"))
val ScanEvent.Companion.DOWN_STRING: String
    get() =
        """
            {
              "event": "host-down",
              "host": {
                "ip": "10.0.0.1"
              }
            }
        """.trimIndent()
