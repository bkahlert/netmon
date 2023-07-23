package com.bkahlert.exec

expect class ShellScript(
    script: String,
) {
    fun execute(): Sequence<String>
}
