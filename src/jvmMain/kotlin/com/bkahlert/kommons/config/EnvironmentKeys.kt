package com.bkahlert.kommons.config

/** The environment variable that configures the setting at the given [path], e.g. `NETWORK_MIN_HOST_BITS` for `network.minHostBits`. */
internal fun environmentKey(path: List<String>): String = path.joinToString("_") { it.toScreamingSnakeCase() }

private val camelCaseWordBoundary = Regex("(?<=[a-z0-9])(?=[A-Z])")

private fun String.toScreamingSnakeCase(): String = replace(camelCaseWordBoundary, "_").uppercase()
