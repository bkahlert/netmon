package com.bkahlert.netmon.ui

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.IP
import com.bkahlert.netmon.Status
import com.bkahlert.netmon.fritz2.runTest
import dev.fritz2.core.RootStore
import dev.fritz2.core.render
import io.kotest.matchers.string.shouldContain
import kotlinx.browser.document
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.w3c.dom.HTMLElement
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class NetworkKtTest {

    @Test
    fun a_cards_since_text_follows_the_clock_it_is_given() = runTest {
        val now = Clock.System.now()
        val clock = MutableStateFlow(now)
        val store = RootStore(listOf(Host(ip = IP.of("192.168.1.1"), status = Status.UP, since = now - 30.seconds)), job = job)
        val container = document.createElement("div") as HTMLElement
        document.body?.appendChild(container)
        render(container) { hosts(store, clock = clock) }
        delay(50)
        container.textContent shouldContain "since 30s"

        clock.value = now + 90.seconds
        delay(50)

        container.textContent shouldContain "since 2m"
    }
}
