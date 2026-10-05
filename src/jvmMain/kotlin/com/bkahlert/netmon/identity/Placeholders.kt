package com.bkahlert.netmon.identity

/** Names that identify nothing: router defaults, MAC-like and UUID names, and chip makers' defaults. */
object Placeholders {

    private val patterns: List<Regex> = listOf(
        Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE),
        // A UUID without dashes, which Home Assistant uses as its mDNS host name.
        Regex("^[0-9a-f]{32}$", RegexOption.IGNORE_CASE),
        // A bare MAC or EUI-64, which Matter devices use as their mDNS host name.
        Regex("^[0-9a-f]{12}(?:[0-9a-f]{4})?$", RegexOption.IGNORE_CASE),
        Regex("^none$", RegexOption.IGNORE_CASE),
        Regex("^PC-(?:\\d{1,3}-){3}\\d{1,3}$"),
        Regex("^PC-(?:[0-9a-f]{2}-){5}[0-9a-f]{2}$", RegexOption.IGNORE_CASE),
        Regex("^PC---.*"),
        Regex("^android-[0-9a-f]+$", RegexOption.IGNORE_CASE),
        Regex("^espressif$", RegexOption.IGNORE_CASE),
        Regex("^ESP[-_][0-9a-f]+$", RegexOption.IGNORE_CASE),
    )

    /** Returns [name] without a trailing dot or `.local`, or `null` if what remains is blank or a placeholder. */
    fun clean(name: String): String? = name.trim()
        .removeSuffix(".")
        .removeSuffix(".local")
        .takeIf { it.isNotEmpty() && patterns.none { pattern -> pattern.matches(it) } }
}
