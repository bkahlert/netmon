package com.bkahlert.netmon.identity

object MacAddresses {

    /** Returns `true` if the locally administered bit is set, so the address identifies no vendor. */
    fun isPrivate(mac: String): Boolean = mac.getOrNull(1)?.lowercaseChar() in PRIVATE_SECOND_DIGITS

    private val PRIVATE_SECOND_DIGITS = setOf('2', '6', 'a', 'e')
}
