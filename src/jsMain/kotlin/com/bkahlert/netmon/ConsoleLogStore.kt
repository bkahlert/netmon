package com.bkahlert.netmon

import com.bkahlert.kommons.js.Console
import com.bkahlert.kommons.js.DefaultConsoleLogFormatter
import com.bkahlert.kommons.js.console
import com.bkahlert.kommons.js.format
import com.bkahlert.kommons.js.tee
import dev.fritz2.core.RootStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map

/** A store initialized with [initial] that records formatted messages from [console] at [levels]. */
class ConsoleLogStore(
    initial: Pair<String, String>,
    job: Job,
    private vararg val levels: String = arrayOf("error", "warn", "info"),
    private val console: Console = com.bkahlert.kommons.js.console,
) : RootStore<Pair<String, String>>(initial, job = job) {

    init {
        console.asDynamic()[initial.first](initial.second)
        console.tee(*levels)
            .map { (fn, args) -> fn to DefaultConsoleLogFormatter.format(args) }
            .handledBy(update)
    }
}
