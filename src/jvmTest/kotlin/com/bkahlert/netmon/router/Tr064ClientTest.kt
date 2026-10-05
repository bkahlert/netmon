package com.bkahlert.netmon.router

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import kotlin.test.Test

class Tr064ClientTest {

    @Test
    fun an_action_without_rights_needs_no_credentials() {
        FakeFritzBox().use { box ->
            val result = Tr064Client(box.base, credentials = null).hosts("GetHostNumberOfEntries")

            result shouldContain ("HostNumberOfEntries" to "2")
        }
    }

    @Test
    fun a_401_is_answered_with_digest_and_the_body_is_sent_both_times() {
        FakeFritzBox().use { box ->
            val result = Tr064Client(box.base, Credentials("netmon", "secret")).hosts("X_AVM-DE_GetHostListPath")

            result shouldContain ("X_AVM-DE_HostListPath" to "/devicehostlist.lua?sid=abc")
            box.requests shouldHaveSize 2
            box.requests[0] shouldStartWith "X_AVM-DE_GetHostListPath - "
            box.requests[1] shouldContain "Digest username=\"netmon\""
            box.requests.none { it.endsWith(" 0") } shouldBe true
        }
    }

    @Test
    fun wrong_credentials_raise_after_one_retry() {
        FakeFritzBox().use { box ->
            val error = shouldThrow<Tr064Exception> { Tr064Client(box.base, Credentials("netmon", "wrong")).hosts("X_AVM-DE_GetHostListPath") }

            error.message shouldContain "401"
            box.requests shouldHaveSize 2
        }
    }

    @Test
    fun a_fault_raises_with_the_boxes_description() {
        FakeFritzBox().use { box ->
            val error = shouldThrow<Tr064Exception> { Tr064Client(box.base, Credentials("netmon", "secret")).hosts("NoSuchAction") }

            error.message shouldContain "Invalid Action"
        }
    }

    @Test
    fun get_streams_a_path_relative_to_the_base() {
        FakeFritzBox().use { box ->
            val result = Tr064Client(box.base, credentials = null).get("/devicehostlist.lua?sid=abc").use { it.readBytes().decodeToString() }

            result shouldContain "<HostName>LEDVANCE-Hallway-TV</HostName>"
        }
    }
}
