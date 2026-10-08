package com.bkahlert.netmon.contract

import com.bkahlert.netmon.support.text.Template
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ScanTopicsTest {

    @Test
    fun topic_uses_the_supplied_template() {
        val source = EventSource("node", "en0", Cidr(IP.of("192.168.0.1"), 24))
        val template = Template("custom/\${node}/\${interface}/\${cidr}/scan")

        val topic = ScanTopics.topic(template, source)

        topic shouldBe "custom/node/en0/192.168.0.1/24/scan"
    }

    @Test
    fun subscription_preserves_the_cidr_topic_segments() {
        val template = Template("dt/netmon/\${node}/\${interface}/\${cidr}/scan")

        val subscription = ScanTopics.subscription(template)

        subscription shouldBe "dt/netmon/+/+/+/+/scan"
    }

    @Test
    fun pattern_and_parser_use_the_supplied_template() {
        val source = EventSource("node", "en0", Cidr(IP.of("192.168.0.1"), 24))
        val template = Template("broker.v1/\${node}/\${interface}/\${cidr}/scan")
        val topic = ScanTopics.topic(template, source)

        val pattern = ScanTopics.pattern(template)
        val matches = pattern.matches(topic)
        val parsed = EventSource.fromTopic(topic, template)

        matches shouldBe true
        parsed shouldBe source
    }
}
