package com.bkahlert.netmon.ui

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.IP
import com.bkahlert.netmon.Status
import com.bkahlert.netmon.fritz2.runTest
import dev.fritz2.core.RootStore
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class NetworkKtTest {

    @Test
    fun a_cards_since_text_follows_the_clock_it_is_given() = runTest {
        val now = Clock.System.now()
        val clock = MutableStateFlow(now)
        val store = RootStore(listOf(Host(ip = IP.of("192.168.1.1"), status = Status.UP, since = now - 30.seconds)), job = job)
        val container = rendered { hosts(store, clock = clock) }
        container.textOnce("since 30s") shouldContain "since 30s"

        clock.value = now + 90.seconds

        container.textOnce("since 2m") shouldContain "since 2m"
        container.remove()
    }
}
