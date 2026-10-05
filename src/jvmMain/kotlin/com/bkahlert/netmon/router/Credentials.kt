package com.bkahlert.netmon.router

/** A user and password; the password never appears in text. */
data class Credentials(val user: String, val password: String) {
    override fun toString(): String = "Credentials(user=$user)"
}
