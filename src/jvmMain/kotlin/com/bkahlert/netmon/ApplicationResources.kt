package com.bkahlert.netmon

internal class ApplicationResources(publisher: AutoCloseable) : AutoCloseable {
    private val resources = NetworkResources().apply { own(publisher) }

    fun ownWorkerManager(stop: () -> Unit) {
        resources.own(AutoCloseable(stop))
    }

    override fun close() = resources.close()
}
