package com.bkahlert.netmon

import com.bkahlert.kommons.config.withTestConfig
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class HostEventSettingsTest {

    @Test
    fun default() {
        withTestConfig {
            val topic = HostEventSettings.topic
            topic.fields.shouldContainExactly("node", "interface", "cidr")
        }
    }

    @Test
    fun configured() {
        withTestConfig("host.topic" to "custom/\${node}-\${interface}/\${cidr}") {
            val topic = HostEventSettings.topic
            topic.text shouldBe "custom/\${node}-\${interface}/\${cidr}"
        }
    }
}
