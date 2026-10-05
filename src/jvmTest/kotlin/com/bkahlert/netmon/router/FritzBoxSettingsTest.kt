package com.bkahlert.netmon.router

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlin.test.Test

class FritzBoxSettingsTest {

    @Test
    fun neither_the_user_name_nor_the_password_appears_in_the_settings_text() {
        System.setProperty("fritzbox.user", "netmon")
        System.setProperty("fritzbox.password", "s3cret")
        try {
            val text = FritzBoxSettings.toString()

            text shouldContain "user=<set>"
            text shouldContain "password=***"
            text shouldNotContain "netmon"
            text shouldNotContain "s3cret"
        } finally {
            System.clearProperty("fritzbox.user")
            System.clearProperty("fritzbox.password")
        }
    }

    @Test
    fun the_credentials_text_holds_neither_user_name_nor_password() {
        val text = Credentials("netmon", "s3cret").toString()

        text shouldContain "user=<set>"
        text shouldNotContain "netmon"
        text shouldNotContain "s3cret"
    }

    @Test
    fun a_password_with_quotes_and_backslashes_is_read_verbatim_and_stays_out_of_the_text() {
        val password = "pa\"ss\\w\\nd"
        System.setProperty("fritzbox.user", "netmon")
        System.setProperty("fritzbox.password", password)
        try {
            FritzBoxSettings.password shouldBe password
            FritzBoxSettings.credentials shouldBe Credentials("netmon", password)

            val text = FritzBoxSettings.toString()

            text shouldContain "password=***"
            text shouldNotContain "pa\"ss"
            text shouldNotContain "w\\nd"
        } finally {
            System.clearProperty("fritzbox.user")
            System.clearProperty("fritzbox.password")
        }
    }
}
