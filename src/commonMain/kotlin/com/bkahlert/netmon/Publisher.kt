package com.bkahlert.netmon

fun interface Publisher<T> {
    fun publish(event: T): PublicationResult
}

sealed interface PublicationResult {
    interface Success : PublicationResult
    interface Failure : PublicationResult
}
