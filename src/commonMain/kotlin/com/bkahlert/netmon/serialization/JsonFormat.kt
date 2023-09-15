package com.bkahlert.netmon.serialization

import kotlinx.serialization.json.Json

val JsonFormat: Json = Json {
    isLenient = true
    ignoreUnknownKeys = true
    explicitNulls = false
    prettyPrint = true
}
