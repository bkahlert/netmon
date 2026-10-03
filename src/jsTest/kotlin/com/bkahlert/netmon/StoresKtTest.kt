package com.bkahlert.netmon

import com.bkahlert.netmon.fritz2.runTest
import io.kotest.matchers.shouldBe
import org.w3c.fetch.NO_STORE
import org.w3c.fetch.RequestCache
import org.w3c.fetch.RequestInit
import org.w3c.fetch.Response
import org.w3c.fetch.ResponseInit
import kotlin.js.Promise
import kotlin.test.Test

class StoresKtTest {

    @Test
    fun loads_the_sample_served_next_to_the_page_past_every_cache() = runTest {
        val requests = mutableListOf<Pair<String, RequestInit>>()
        val fetch = { input: String, init: RequestInit ->
            requests += input to init
            Promise.resolve(Response("""{"at":1759450000,"interval":5,"kioskCpu":118,"webCpu":114,"kioskMemory":168820736}"""))
        }

        val stats = loadKioskStats(fetch)

        stats shouldBe KioskStats(at = 1759450000, interval = 5, kioskCpu = 118, webCpu = 114, kioskMemory = 168_820_736)
        requests.map { it.first } shouldBe listOf("stats.json")
        requests.single().second.cache shouldBe RequestCache.NO_STORE
    }

    @Test
    fun has_no_sample_while_the_file_is_missing() = runTest {
        val stats = loadKioskStats { _, _ -> Promise.resolve(Response("Not Found", ResponseInit(status = 404))) }

        stats shouldBe null
    }

    @Test
    fun has_no_sample_from_a_reply_that_is_not_one() = runTest {
        val stats = loadKioskStats { _, _ -> Promise.resolve(Response("<html lang=\"en\">")) }

        stats shouldBe null
    }

    @Test
    fun has_no_sample_when_the_request_fails() = runTest {
        val stats = loadKioskStats { _, _ -> Promise.reject(Throwable("offline")) }

        stats shouldBe null
    }
}
