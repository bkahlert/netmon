package com.bkahlert.netmon.scanner.app

internal class NetworkResources : AutoCloseable {
    private val resources = mutableListOf<AutoCloseable>()
    private var closed = false

    @Synchronized
    fun <T : AutoCloseable> own(resource: T): T {
        check(!closed) { "Network resources are already closed" }
        resources += resource
        return resource
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true

        var failure: Throwable? = null
        resources.asReversed().forEach { resource ->
            try {
                resource.close()
            } catch (closeFailure: Throwable) {
                val firstFailure = failure
                if (firstFailure == null) {
                    failure = closeFailure
                } else if (firstFailure !== closeFailure) {
                    firstFailure.addSuppressed(closeFailure)
                }
            }
        }
        resources.clear()
        failure?.let { throw it }
    }
}
