package com.bkahlert.netmon.mqtt

fun interface Publisher<T> {
    fun publish(topic: String, event: T): Boolean
}
