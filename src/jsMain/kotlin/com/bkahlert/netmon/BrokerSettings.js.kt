package com.bkahlert.netmon

import kotlinx.browser.window

/** The host the page was loaded from; `localhost` for pages without one, such as `file:` URLs. */
internal actual val defaultBrokerHost: String
    get() = window.location.hostname.ifEmpty { "localhost" }
