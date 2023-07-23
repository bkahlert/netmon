package com.bkahlert.netmon

fun interface Publisher<T> {
    fun publish(topic: String, event: T)
}
