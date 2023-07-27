package com.bkahlert.netmon

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@JvmInline
@Serializable
value class Cidr(val value: String) {

    init {
        require(value.matches(Regex("""[^/]+/\d+"""))) { "Invalid CIDR: $value" }
    }

    val ip: IP get() = IP(value.substringBefore('/'))
    val mask: Int get() = value.substringAfter('/').toInt()

    val filenameString: String get() = "${ip.filenameString}_$mask"

    init {
        require(mask in 0..128) { "Invalid mask: $mask" }
    }

    override fun toString(): String = value
}
