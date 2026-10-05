package com.bkahlert.netmon.router

import com.bkahlert.kommons.config.Settings
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.StringFormat
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/** The FRITZ!Box to read the host table from; `url` defaults to the `_tr064._tcp` record, then `http://fritz.box:49000`. */
object FritzBoxSettings : Settings("fritzbox") {
    val url: String? by setting()
    val user: String? by setting(stringFormat = VerbatimStrings)
    val password: String? by setting(stringFormat = VerbatimStrings)

    val credentials: Credentials? get() = user?.let { u -> password?.let { p -> Credentials(u, p) } }

    override fun toString(): String = "FritzBoxSettings[url=$url, user=$user, password=${password?.let { "***" }}]"

    /** Reads a string setting as it is written, so a password with quotes or backslashes is not parsed as JSON. */
    private object VerbatimStrings : StringFormat {
        override val serializersModule: SerializersModule = EmptySerializersModule()

        override fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T): String =
            value as? String ?: throw IllegalArgumentException("Only strings are supported")

        @Suppress("UNCHECKED_CAST")
        override fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, string: String): T {
            require(deserializer.descriptor.kind == PrimitiveKind.STRING) { "Only strings are supported" }
            return string as T
        }
    }
}
