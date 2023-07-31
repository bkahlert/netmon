package com.bkahlert.kommons.js

fun interface ConsoleLogFormatter {
    fun format(message: String, args: List<dynamic>): String
}

fun ConsoleLogFormatter.format(vararg args: dynamic): String = format(args)

fun ConsoleLogFormatter.format(args: Array<dynamic>): String = format(
    message = args.firstOrNull()?.toString() ?: "",
    args = args.drop(1)
)

// TODO parametrize formatting rules
/**
 * Formats the message with the given values.
 *
 * Specifiers are handled as follows:
 * - `%s` is formatted as a [String]
 * - `%i` and `%d` are formatted as [Int]
 * - `%f` is formatted as [Float]
 * - `%o` used to be treated as an expandable DOM element, but is treated like `%O`
 * - `%O` is formatted an JavaScript object
 * - `%c` used to be treated as a CSS style declaration but is ignored
 * - `%%` is replaced with a `%`
 */
object DefaultConsoleLogFormatter : ConsoleLogFormatter {

    override fun format(message: String, args: List<dynamic>): String {
        val remainingArgs = args.toMutableList()
        val messageWithFormattedSubstitutions = message.replace(Regex("%[%idfoOcs]")) { match ->
            when (val specifier = match.value) {
                "%%" -> "%"
                else -> {
                    if (remainingArgs.isEmpty()) match.value
                    else formatArg(specifier, remainingArgs.removeFirst())
                }
            }
        }

        return if (remainingArgs.isEmpty()) {
            messageWithFormattedSubstitutions
        } else {
            val formattedRemainingArgs = remainingArgs.joinToString(" ") { formatArg("%s", it) }
            "$messageWithFormattedSubstitutions $formattedRemainingArgs"
        }
    }

    private fun formatArg(specifier: String, arg: dynamic): String = when (specifier) {
        "%i", "%d", "%f" -> js("Number(arg)").toString()
        "%o", "%O" -> JSON.stringify(arg)
        "%c" -> ""
        else -> {
            val str = js("String(arg)").unsafeCast<String>()
            val isObject = js("typeof arg === 'object'").unsafeCast<Boolean>()
            if (isObject && str == "[object Object]") JSON.stringify(arg)
            else str
        }
    }
}
