package com.bkahlert.netmon.scanner.mqtt

fun interface Publisher<T> {
    fun publish(topic: String, event: T): Boolean
}
