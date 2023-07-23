package com.bkahlert.term

val ANSI_BOLD = "\u001B[1m"
val ANSI_RESET = "\u001B[0m"

fun String.bold(): String = "$ANSI_BOLD$this$ANSI_RESET"
