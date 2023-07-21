package com.bkahlert.io

data object Logger {
    private val logLevel = LogLevel.INFO

    private fun print(
        logLevel: LogLevel,
        message: () -> String,
    ) = if (logLevel >= this.logLevel) println(message()) else Unit

    fun error(message: String) = print(LogLevel.ERROR) { red(message) }
    fun info(message: String) = print(LogLevel.INFO) { gray(message) }
    fun debug(message: String) = print(LogLevel.DEBUG) { gray(italic(message)) }
}

enum class LogLevel {
    TRACE, DEBUG, INFO, WARNING, ERROR;
}

private fun italic(message: String) = "\u001B[3m$message\u001B[23m"
private fun gray(message: String) = "\u001B[90m$message\u001B[39m"
private fun red(message: String) = "\u001B[31m$message\u001B[39m"
