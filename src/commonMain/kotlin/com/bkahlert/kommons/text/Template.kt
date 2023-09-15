package com.bkahlert.kommons.text

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A template is a string that contains fields of the form `${<field>}` that can be substituted with values.
 */
@Serializable(with = TemplateSerializer::class)
class Template(val text: String) : CharSequence by text {

    /** The fields contained in this template. */
    val fields: Set<String> by lazy { findFields(text).toSet() }

    /**
     * Substitutes all fields in this template with the values from the given [substitutions].
     *
     * @throws IllegalArgumentException if [substitutions] does not contain all fields or contains additional fields.
     */
    fun toString(vararg substitutions: Pair<String, CharSequence>): String = toString(substitutions.toMap())

    /**
     * Substitutes all fields in this template with the values from the given [substitutions].
     *
     * @throws IllegalArgumentException if [substitutions] does not contain all fields or contains additional fields.
     */
    fun toString(substitutions: Map<String, CharSequence>): String {
        val existingFields = fields
        val givenFields = substitutions.keys
        val missingFields = existingFields - givenFields
        val extraFields = givenFields - existingFields
        if (missingFields.isNotEmpty()) throw IllegalArgumentException("Missing substitutions: $missingFields")
        if (extraFields.isNotEmpty()) throw IllegalArgumentException("Extra substitutions: $extraFields")
        return substitutions.entries.fold(text) { text, (field, value) -> substituteFields(text, field, value) }
    }

    override fun toString(): String = text

    companion object {
        private val templateRegex = Regex("""\$\{(?<field>[^}]+)\}""")

        /** Finds all fields in the given [text], whereas a field is defined as `${<field>}`. */
        fun findFields(text: String): Sequence<String> =
            templateRegex.findAll(text).mapNotNull { it.groups["field"]?.value }.distinct()

        /** Substitutes the provided [field] with the given [value] in the given [text]. */
        @Suppress("NOTHING_TO_INLINE")
        inline fun substituteFields(text: String, field: String, value: String): String =
            text.replace("\${$field}", value)

        /** Substitutes the provided [field] with the given [value] in the given [text]. */
        @Suppress("NOTHING_TO_INLINE")
        inline fun substituteFields(text: String, field: String, value: CharSequence): String =
            substituteFields(text, field, value.toString())
    }
}

data object TemplateSerializer : KSerializer<Template> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Status", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Template) {
        encoder.encodeString(value.text)
    }

    override fun deserialize(decoder: Decoder): Template =
        Template(decoder.decodeString())
}
