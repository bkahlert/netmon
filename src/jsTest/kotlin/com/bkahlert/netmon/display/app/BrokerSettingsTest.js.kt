package com.bkahlert.netmon.display.app

import com.bkahlert.netmon.support.config.withTestConfig
import io.kotest.matchers.shouldBe
import kotlinx.browser.window
import kotlin.test.Test

class BrokerSettingsTest {

    @Test
    fun host_defaults_to_the_host_the_page_was_loaded_from() = withTestConfig {
        BrokerSettings.host shouldBe window.location.hostname
    }

    @Test
    fun host_from_query_wins() = withTestConfig("broker.host" to "broker.example.com") {
        BrokerSettings.host shouldBe "broker.example.com"
    }

    @Test
    fun port_defaults_to_the_websocket_listener() = withTestConfig {
        BrokerSettings.port shouldBe 8080
    }
}
