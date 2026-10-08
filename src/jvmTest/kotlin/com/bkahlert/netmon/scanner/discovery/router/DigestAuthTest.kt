package com.bkahlert.netmon.scanner.discovery.router

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test

class DigestAuthTest {

    @Test
    fun computes_the_rfc_2617_example_response() {
        val challenge = """Digest realm="testrealm@host.com", qop="auth,auth-int", nonce="dcd98b7102dd2f0e8b11d0f600bfb0c093", opaque="5ccc069c403ebaf9f0171e9517f40e41""""

        val result = DigestAuth.authorization(challenge, "GET", "/dir/index.html", Credentials("Mufasa", "Circle Of Life"), cnonce = "0a4f113b")

        result shouldContain """response="6629fae49393a05397450978507c4ef1""""
        result shouldContain """username="Mufasa""""
        result shouldContain """realm="testrealm@host.com""""
        result shouldContain """uri="/dir/index.html""""
        result shouldContain "qop=auth, nc=00000001, cnonce=\"0a4f113b\""
        result shouldContain """opaque="5ccc069c403ebaf9f0171e9517f40e41""""
    }

    @Test
    fun a_challenge_without_qop_uses_the_legacy_response() {
        val challenge = """Digest realm="HTTPS Access", nonce="ABCDEF0123456789""""

        val result = DigestAuth.authorization(challenge, "POST", "/upnp/control/hosts", Credentials("u", "p"), cnonce = "x")

        result shouldBe """Digest username="u", realm="HTTPS Access", nonce="ABCDEF0123456789", uri="/upnp/control/hosts", response="${DigestAuth.md5("${DigestAuth.md5("u:HTTPS Access:p")}:ABCDEF0123456789:${DigestAuth.md5("POST:/upnp/control/hosts")}")}""""
    }
}
