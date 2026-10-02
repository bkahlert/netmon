package com.bkahlert.kommons

@JvmInline
value class Pid(val value: Long) {
    override fun toString(): String = "PID($value)"

    companion object {
        val current: Pid by lazy { Pid(ProcessHandle.current().pid()) }
    }
}
