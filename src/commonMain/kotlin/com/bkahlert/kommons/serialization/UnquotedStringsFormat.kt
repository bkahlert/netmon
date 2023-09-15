package com.bkahlert.kommons.serialization

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.StringFormat
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.modules.SerializersModule

/**
 * [StringFormat] delegates to the specified [delegate] but
 * serializes strings unquoted, and
 * deserializes unquoted strings as regular strings.
 */
class UnquotedStringsFormat(private val delegate: StringFormat) : StringFormat {

    override val serializersModule: SerializersModule get() = delegate.serializersModule

    override fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T): String {
        val jsonString = delegate.encodeToString(serializer, value)
        return if (value is String) jsonString.removeSurrounding("\"") else jsonString
    }

    override fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, string: String): T {
        val actualString = when (deserializer.descriptor.kind) {
            PrimitiveKind.STRING -> "\"$string\""
            else -> string
        }
        return delegate.decodeFromString(deserializer, actualString)
    }

    companion object {
        /** An [UnquotedStringsFormat] that delegates to this [StringFormat]. */
        val StringFormat.unquoted: StringFormat get() = UnquotedStringsFormat(this)
    }
}
