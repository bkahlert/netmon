package com.bkahlert.netmon.router

import java.security.MessageDigest
import java.security.SecureRandom

/** HTTP Digest authentication (RFC 7616) with MD5, as FRITZ!OS speaks it. */
object DigestAuth {

    private val parameter = Regex("""(?<name>\w+)=(?:"(?<quoted>[^"]*)"|(?<bare>[^,\s]*))""")

    /** Returns the `Authorization` header value answering [challenge] for a [method] request of [uri]. */
    fun authorization(
        challenge: String,
        method: String,
        uri: String,
        credentials: Credentials,
        cnonce: String = randomHex(),
        nc: String = "00000001",
    ): String {
        val params = parameter.findAll(challenge.removePrefix("Digest").trim())
            .associate { it.groups["name"]!!.value to (it.groups["quoted"] ?: it.groups["bare"])!!.value }
        val realm = params["realm"] ?: throw Tr064Exception("Digest challenge without realm: $challenge")
        val nonce = params["nonce"] ?: throw Tr064Exception("Digest challenge without nonce: $challenge")
        val qop = params["qop"]?.split(',')?.map { it.trim() }?.firstOrNull { it == "auth" }
        val ha1 = md5("${credentials.user}:$realm:${credentials.password}")
        val ha2 = md5("$method:$uri")
        val response = if (qop != null) md5("$ha1:$nonce:$nc:$cnonce:$qop:$ha2") else md5("$ha1:$nonce:$ha2")
        return buildString {
            append("Digest username=\"${credentials.user}\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$uri\", response=\"$response\"")
            if (qop != null) append(", qop=$qop, nc=$nc, cnonce=\"$cnonce\"")
            params["opaque"]?.let { append(", opaque=\"$it\"") }
            params["algorithm"]?.let { append(", algorithm=$it") }
        }
    }

    fun md5(text: String): String = MessageDigest.getInstance("MD5").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun randomHex(): String = ByteArray(8).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
}
