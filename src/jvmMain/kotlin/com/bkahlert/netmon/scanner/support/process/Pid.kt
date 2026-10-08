package com.bkahlert.netmon.scanner.support.process

@JvmInline
value class Pid(val value: Long) {
    override fun toString(): String = "PID($value)"

    companion object {
        val current: Pid by lazy { Pid(ProcessHandle.current().pid()) }
    }
}
