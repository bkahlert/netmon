package com.bkahlert.netmon.router

import com.bkahlert.kommons.config.Settings

/** The FRITZ!Box to read the host table from; `url` defaults to the `_tr064._tcp` record, then `http://fritz.box:49000`. */
object FritzBoxSettings : Settings("fritzbox") {
    val url: String? by setting()
    val user: String? by setting()
    val password: String? by setting()

    val credentials: Credentials? get() = user?.let { u -> password?.let { p -> Credentials(u, p) } }

    override fun toString(): String = "FritzBoxSettings[url=$url, user=$user, password=${password?.let { "***" }}]"
}
