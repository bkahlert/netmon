package com.bkahlert.netmon.display.support

import com.bkahlert.netmon.display.support.console.Console
import com.bkahlert.netmon.display.support.console.DefaultConsoleLogFormatter
import com.bkahlert.netmon.display.support.console.console
import com.bkahlert.netmon.display.support.console.format
import com.bkahlert.netmon.display.support.console.tee
import com.bkahlert.netmon.display.support.console.console as browserConsole
import dev.fritz2.core.RootStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map

/** A store initialized with [initial] that records formatted messages from [console] at [levels]. */
class ConsoleLogStore(
    initial: Pair<String, String>,
    job: Job,
    private vararg val levels: String = arrayOf("error", "warn", "info"),
    private val console: Console = browserConsole,
) : RootStore<Pair<String, String>>(initial, job = job) {

    init {
        console.asDynamic()[initial.first](initial.second)
        console.tee(*levels)
            .map { (fn, args) -> fn to DefaultConsoleLogFormatter.format(args) }
            .handledBy(update)
    }
}
