package com.bkahlert.netmon.display.app

import com.bkahlert.netmon.support.config.Settings
import com.bkahlert.netmon.support.serialization.UnquotedStringsFormat.Companion.unquoted
import com.bkahlert.netmon.contract.serialization.JsonFormat
import kotlinx.browser.window

object BrokerSettings : Settings("broker", JsonFormat.unquoted) {

    /** The host the page was loaded from; `localhost` for pages without one, such as `file:` URLs. */
    val host: String by setting(default = window.location.hostname.ifEmpty { "localhost" })

    /** The port of the MQTT broker used by the browser. */
    val port: Int by setting(default = 8080)
}
