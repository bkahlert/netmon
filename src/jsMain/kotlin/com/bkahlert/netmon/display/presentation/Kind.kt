package com.bkahlert.netmon.display.presentation

import com.bkahlert.netmon.contract.Kind

/** The token split into words, for example `Set Top Box`. */
val Kind.label: String get() = token.replace(WORD_BOUNDARY, " ")

private val WORD_BOUNDARY = Regex("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")
