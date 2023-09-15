package com.bkahlert.netmon

import com.bkahlert.kommons.config.withTestConfig
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ScanEventSettingsTest {

    @Test
    fun default() {
        withTestConfig {
            val topic = ScanEventSettings.topic
            topic.fields.shouldContainExactly("node", "interface", "cidr")
        }
    }

    @Test
    fun configured() {
        withTestConfig("scan.topic" to "custom/\${node}-\${interface}/\${cidr}") {
            val topic = ScanEventSettings.topic
            topic.text shouldBe "custom/\${node}-\${interface}/\${cidr}"
        }
    }
}
