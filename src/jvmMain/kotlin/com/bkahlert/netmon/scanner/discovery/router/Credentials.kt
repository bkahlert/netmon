package com.bkahlert.netmon.scanner.discovery.router

/** A user and password; neither appears in text. */
data class Credentials(val user: String, val password: String) {
    override fun toString(): String = "Credentials(user=<set>)"
}
