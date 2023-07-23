package com.bkahlert.netmon

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@JvmInline
@Serializable
value class Cidr(val value: String) {

    init {
        require(value.matches(Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}/\d{1,2}"""))) { "Invalid CIDR: $value" }
    }

    val ip: String get() = value.substringBefore('/')
    val mask: Int get() = value.substringAfter('/').toInt()

    init {
        require(ip.split('.').all { it.toInt() in 0..255 }) { "Invalid IP: $ip" }
        require(mask in 0..32) { "Invalid mask: $mask" }
    }

    override fun toString(): String = value
}
