package com.bkahlert.netmon.router

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlin.test.Test

class FritzBoxSettingsTest {

    @Test
    fun the_password_never_appears_in_the_settings_text() {
        System.setProperty("fritzbox.user", "netmon")
        System.setProperty("fritzbox.password", "s3cret")
        try {
            val text = FritzBoxSettings.toString()

            text shouldContain "user=netmon"
            text shouldContain "password=***"
            text shouldNotContain "s3cret"
        } finally {
            System.clearProperty("fritzbox.user")
            System.clearProperty("fritzbox.password")
        }
    }
}
