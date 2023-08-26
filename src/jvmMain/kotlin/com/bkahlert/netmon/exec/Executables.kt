package com.bkahlert.netmon.exec

import com.bkahlert.kommons.exec.CommandLine
import com.bkahlert.kommons.exec.ShellScript
import com.bkahlert.kommons.io.toPath
import com.bkahlert.kommons.time.Now
import java.lang.management.ManagementFactory
import kotlin.io.path.createFile
import kotlin.io.path.writeText
import kotlin.math.absoluteValue
import kotlin.random.Random

/** Returns a [CommandLine] that runs this [CommandLine] in the background and closes it when the JVM exits. */
val CommandLine.autoKilling: CommandLine
    get() {
        val random = Random.nextInt().absoluteValue
        val javaPid = ManagementFactory.getRuntimeMXBean().name.split("@").first().toInt()
        val log = "/tmp/exec-${javaPid}-${Now.epochSeconds}-$random.log"
        log.toPath().createFile().apply {
            writeText("Starting command line auto-killing, java PID=$javaPid: ${this@autoKilling}\n")
        }
        return ShellScript(
            """
            ${toString()} &
            {
                watched_pid=${'$'}!
                while true; do
                  kill -0 $javaPid 2>/dev/null || {
                    ! kill -0 ${'$'}watched_pid 2>/dev/null || kill ${'$'}watched_pid
                    exit
                  }
                  kill -0 ${'$'}watched_pid 2>/dev/null || break
                  sleep 1
                done
                wait ${'$'}watched_pid
                exit_code=${'$'}?
                [ ! -f '$log' ] || rm '$log' >/dev/null 2>&1
                exit ${'$'}?
            } >>'$log' 2>&1
            """.trimIndent()
        ).toCommandLine()
    }

/** Returns a [CommandLine] that runs this [ShellScript] in the background and closes it when the JVM exits. */
val ShellScript.autoKilling: CommandLine
    get() = toCommandLine().autoKilling
