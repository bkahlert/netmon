package com.bkahlert.netmon

import kotlinx.serialization.Serializable
import java.math.BigInteger
import java.net.InetAddress

@JvmInline
@Serializable
value class IP(val value: String) {

    val addr: InetAddress get() = InetAddress.getByName(value)
    val bytes: ByteArray get() = addr.address
    val intValue: BigInteger get() = BigInteger(1, bytes)

    val filenameString: String
        get() = value
            .replace('.', '-')
            .replace(':', '-')

    override fun toString(): String = value
}
