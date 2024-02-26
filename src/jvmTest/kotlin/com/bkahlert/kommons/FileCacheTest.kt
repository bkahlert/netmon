package com.bkahlert.kommons

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class FileCacheTest {

    private val cacheName = "kommons-test"
    private val cache = FileCache.of(cacheName)

    @Test
    fun location() {
        cache.directory shouldBe SystemLocations.Cache.resolve(cacheName)
    }

    @Test
    fun to_string() {
        cache.put("file 1", createTempFile())
        cache.put("file 2", createTempFile())
        cache.put("directory", createTempDirectory())
        cache.toString() shouldMatch Regex("FileCache\\(location=file://${cache.directory}, size=\\d+ B, directoryCount=1, fileCount=2\\)")
    }

    @BeforeTest
    @AfterTest
    fun cleanUp() {
        cache.purge()
    }
}
