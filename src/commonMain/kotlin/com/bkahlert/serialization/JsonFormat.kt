package com.bkahlert.serialization

import kotlinx.serialization.json.Json

val JsonFormat: Json = Json {
    isLenient = true
    ignoreUnknownKeys = true
    explicitNulls = false
    prettyPrint = true
}
