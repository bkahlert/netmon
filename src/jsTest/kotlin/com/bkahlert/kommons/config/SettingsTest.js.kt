package com.bkahlert.kommons.config

import com.bkahlert.kommons.uri.Uri
import com.bkahlert.kommons.uri.formUrlEncode
import io.ktor.http.Parameters

actual fun <R> withTestConfig(vararg config: Pair<String, String>, block: () -> R): R {
    val query = Parameters.build { config.forEach { (key, value) -> append(key, value) } }.formUrlEncode(keepEmptyValues = true)
    UriSource.testUri = Uri(scheme = null, authority = null, path = "", query = query, fragment = null)
    try {
        return block()
    } finally {
        UriSource.testUri = null
    }
}
