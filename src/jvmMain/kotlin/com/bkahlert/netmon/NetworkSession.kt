package com.bkahlert.netmon

import com.bkahlert.netmon.scanner.NetmonScanner

internal class NetworkSession private constructor(
    private val scanner: NetmonScanner,
    private val resources: NetworkResources,
) : AutoCloseable {

    fun scan() = scanner.scan()

    override fun close() = resources.close()

    companion object {
        fun open(createScanner: (NetworkResources) -> NetmonScanner): NetworkSession {
            val resources = NetworkResources()
            return try {
                NetworkSession(createScanner(resources), resources)
            } catch (startupFailure: Throwable) {
                try {
                    resources.close()
                } catch (cleanupFailure: Throwable) {
                    if (startupFailure !== cleanupFailure) {
                        startupFailure.addSuppressed(cleanupFailure)
                    }
                }
                throw startupFailure
            }
        }
    }
}
