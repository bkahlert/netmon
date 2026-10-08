package com.bkahlert.netmon.support.config

import org.w3c.dom.url.URLSearchParams

actual fun <R> withTestConfig(vararg config: Pair<String, String>, block: () -> R): R {
    val query = URLSearchParams().apply { config.forEach { (key, value) -> append(key, value) } }.toString()
    UriSource.testQuery = query
    try {
        return block()
    } finally {
        UriSource.testQuery = null
    }
}
