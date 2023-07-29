package com.bkahlert.netmon.net

import com.bkahlert.netmon.JsonFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import kotlinx.serialization.KSerializer
import kotlinx.serialization.StringFormat
import kotlinx.serialization.serializer
import mqtt.MqttMessage

inline fun <reified T> Flow<MqttMessage>.decode(
    stringFormat: StringFormat = JsonFormat,
    deserializer: KSerializer<T> = serializer<T>(),
): Flow<T> = transform { (_, message, _) ->
    runCatching {
        stringFormat.decodeFromString(deserializer, message.decodeToString())
    }.fold(
        onSuccess = { emit(it) },
        onFailure = { com.bkahlert.kommons.js.console.error("Failed to decode MQTT message", it) }
    )
}
