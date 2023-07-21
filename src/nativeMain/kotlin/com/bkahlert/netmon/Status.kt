package com.bkahlert.netmon

import kotlinx.serialization.Serializable

@Serializable
sealed interface Status {
    @Serializable
    data object UP : Status
    @Serializable
    data object DOWN : Status
    @Serializable
    data class UNKNOWN(val value: String) : Status

    companion object {
        fun of(value: String): Status = when (value.uppercase()) {
            "UP" -> UP
            "DOWN" -> DOWN
            else -> UNKNOWN(value)
        }
    }
}

/*
TODO
Error saving scan result: kotlinx.serialization.SerializationException: Class 'UP' is not registered for polymorphic serialization in the scope of 'Status'.
To be registered automatically, class 'UP' has to be '@Serializable', and the base class 'Status' has to be sealed and '@Serializable'.
 */
