package com.bkahlert.netmon.contract.serialization

import kotlinx.serialization.json.Json

val JsonFormat: Json = Json {
    isLenient = true
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    prettyPrint = true
}
