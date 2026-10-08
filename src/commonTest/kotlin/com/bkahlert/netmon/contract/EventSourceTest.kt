package com.bkahlert.netmon.contract

import com.bkahlert.netmon.support.text.Template
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class EventSourceTest {

    @Test
    fun instantiation() = runTest {
        forAll(
            row("node", "en0", Cidr(IP.of("192.168.0.1"), 24)),
        ) { node, `interface`, cidr ->
            EventSource(node, `interface`, cidr) should {
                it.node shouldBe node
                it.`interface` shouldBe `interface`
                it.cidr shouldBe cidr
            }
        }
    }

    @Test
    fun from_topic() = runTest {
        forAll(
            row("dt/netmon/node/en0/192.168.0.1/24/scan", EventSource("node", "en0", Cidr(IP.of("192.168.0.1"), 24))),
            row("dt/netmon/node/en0/::ffff:c0a8:0001/120/scan", EventSource("node", "en0", Cidr(IP.of("192.168.0.1"), 24))),
            row("dt/netmon/node/en0/2001:db8::/32/scan", EventSource("node", "en0", Cidr(IP.of("2001:db8::"), 32))),
            row("dt/netmon/node/en0/2001:0db8:0000:0000:0000:0000:0000:0000/32/scan", EventSource("node", "en0", Cidr(IP.of("2001:db8::"), 32))),
        ) { text, expected ->
            EventSource.fromTopic(text, scanTopicTemplate) shouldBe expected
        }

        forAll(
            row("dt/netmon/en0/192.168.0.1/24/scan"),
            row("dt/netmon/node//192.168.0.1/24/scan"),
            row("dt/netmon/node/en0//24/scan"),
            row("dt/netmon/node/en0/192.168.0.1//scan"),
            row("dt/netmon/node/en0/scan"),
            row("dt/netmon/node/en0/192.168.0.1/33/scan"),
        ) { topic ->
            shouldThrow<IllegalArgumentException> { EventSource.fromTopic(topic, scanTopicTemplate) }
        }
    }

    @Test
    fun equality() = runTest {
        EventSource("node", "en0", Cidr(IP.of("192.168.0.1"), 24)) should {
            it shouldBe EventSource("node", "en0", Cidr(IP.of("192.168.0.1"), 24))
            it shouldNotBe EventSource("other", "en0", Cidr(IP.of("192.168.0.1"), 24))
            it shouldNotBe EventSource("node", "other0", Cidr(IP.of("192.168.0.1"), 24))
            it shouldNotBe EventSource("node", "en0", Cidr(IP.of("192.168.0.2"), 24))
        }

    }

    @Test
    fun to_string() = runTest {
        forAll(
            row(EventSource("node", "en0", Cidr(IP.of("192.168.0.1"), 24)), "node/en0/192.168.0.1/24"),
            row(EventSource("node", "en0", Cidr(IP.of("2001:db8::"), 32)), "node/en0/2001:db8::/32"),
        ) { cidr, expected ->
            cidr.toString() shouldBe expected
        }
    }
}

private val scanTopicTemplate = Template("dt/netmon/\${node}/\${interface}/\${cidr}/scan")
