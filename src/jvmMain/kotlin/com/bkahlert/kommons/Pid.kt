package com.bkahlert.kommons

import java.lang.management.ManagementFactory
import kotlin.reflect.cast

@JvmInline
value class Pid(val value: Long) {
    override fun toString(): String = "PID($value)"

    companion object {
        private fun currentFromRuntime(): Long = ManagementFactory.getRuntimeMXBean().let { runtime ->
            val management = runtime.javaClass.getDeclaredField("jvm").also { it.isAccessible = true }.get(runtime)
            val pid = management.javaClass.getDeclaredMethod("getProcessId").also { it.isAccessible = true }.invoke(management)
            Int::class.cast(pid).toLong()
        }

        private fun currentFromProcessHandle(): Long = Class.forName("java.lang.ProcessHandle").let { processHandleClass ->
            val processHandle = processHandleClass.getMethod("current").invoke(null)
            val pid = processHandleClass.getMethod("pid").invoke(processHandle)
            Long::class.cast(pid)
        }

        private val javaVersion: Pair<Int, Int> by lazy {
            System.getProperty("java.version").split(".").let { (major, minor) -> major.toInt() to minor.toInt() }
        }

        private val java8OrLower by lazy {
            javaVersion.let { (major, minor) ->
                (major == 1 && minor <= 8) || major <= 8
            }
        }

        val current: Pid by lazy {
            Pid(if (java8OrLower) currentFromRuntime() else currentFromProcessHandle())
        }
    }
}
