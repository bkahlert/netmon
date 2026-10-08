package com.bkahlert.netmon.scanner.support.net

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Passes at most [limit] bytes and closes [delegate] after [timeout], which also ends a blocked read; both surface as an [IOException]. */
internal class BoundedInputStream(delegate: InputStream, private val limit: Long, private val timeout: Duration) : FilterInputStream(delegate) {

    private var count = 0L

    @Volatile
    private var expired = false
    private val deadline: ScheduledFuture<*> = timer.schedule({
        expired = true
        runCatching { delegate.close() }
    }, timeout.toMillis(), TimeUnit.MILLISECONDS)

    override fun read(): Int {
        val buffer = ByteArray(1)
        val n = read(buffer, 0, 1)
        return if (n < 0) -1 else buffer[0].toInt() and 0xff
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        val n = try {
            super.read(b, off, minOf(len.toLong(), limit - count + 1).toInt())
        } catch (e: IOException) {
            if (expired) throw IOException("not finished within $timeout", e)
            throw e
        }
        if (expired) throw IOException("not finished within $timeout")
        if (n > 0) count += n
        if (count > limit) throw IOException("longer than $limit bytes")
        return n
    }

    override fun skip(n: Long): Long = 0

    override fun close() {
        deadline.cancel(false)
        super.close()
    }

    private companion object {
        val timer: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "read-deadline").apply { isDaemon = true }
        }
    }
}
