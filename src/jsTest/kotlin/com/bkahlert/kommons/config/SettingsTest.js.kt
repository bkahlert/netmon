package com.bkahlert.kommons.config

import com.bkahlert.netmon.uri.Uri
import org.w3c.dom.url.URLSearchParams

actual fun <R> withTestConfig(vararg config: Pair<String, String>, block: () -> R): R {
    val query = URLSearchParams().apply { config.forEach { (key, value) -> append(key, value) } }.toString()
    UriSource.testUri = Uri("?$query")
    try {
        return block()
    } finally {
        UriSource.testUri = null
    }
}
