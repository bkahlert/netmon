@file:Suppress("RedundantVisibilityModifier")

package com.bkahlert.netmon.support.test

import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import java.nio.file.Path
import java.nio.file.attribute.FileAttribute
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists

/**
 * Creates an empty [TestScope]-scoped file in the default temp directory, using
 * the given [prefix] and [suffix] to generate its name.
 * @see kotlin.io.path.createTempFile
 */
@Suppress("NOTHING_TO_INLINE")
public inline fun TestScope.createTempFile(
    prefix: String? = null,
    suffix: String? = null,
    vararg attributes: FileAttribute<*>
): Path = kotlin.io.path.createTempFile(prefix, suffix, *attributes).also { tempFile ->
    coroutineContext.job.invokeOnCompletion {
        if (tempFile.exists()) tempFile.deleteRecursively()
    }
}

/**
 * Creates a new [TestScope]-scoped directory in the default temp directory, using the given [prefix] to generate its name.
 * @see kotlin.io.path.createTempDirectory
 */
@Suppress("NOTHING_TO_INLINE")
public inline fun TestScope.createTempDirectory(
    prefix: String? = null,
    vararg attributes: FileAttribute<*>
): Path = kotlin.io.path.createTempDirectory(prefix, *attributes).also { tempDir ->
    coroutineContext.job.invokeOnCompletion {
        if (tempDir.exists()) tempDir.deleteRecursively()
    }
}
