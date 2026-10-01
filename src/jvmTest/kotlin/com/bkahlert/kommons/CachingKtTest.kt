package com.bkahlert.kommons

import com.bkahlert.kommons.test.createTempDirectory
import com.bkahlert.kommons.test.createTempFile
import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.paths.shouldBeADirectory
import io.kotest.matchers.paths.shouldContainFiles
import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.appendLines
import kotlin.io.path.createFile
import kotlin.io.path.getPosixFilePermissions
import kotlin.io.path.pathString
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class CachingKtTest {

    @Test
    fun cache_property() {
        SystemLocations.Cache should { dir ->
            dir.shouldExist()
            dir.shouldBeADirectory()
            dir.getPosixFilePermissions() should { permissions ->
                permissions shouldContain PosixFilePermission.OWNER_READ
                permissions shouldContain PosixFilePermission.OWNER_WRITE
                permissions shouldContain PosixFilePermission.OWNER_EXECUTE
                permissions shouldNotContain PosixFilePermission.GROUP_WRITE
                permissions shouldNotContain PosixFilePermission.OTHERS_WRITE
            }
        }
    }

    @Test
    fun get() = runTest {
        FileCache(createTempDirectory()) should {
            it.get("name") shouldBe null
        }
    }

    @Test
    fun put_stream() = runTest {
        FileCache(createTempDirectory()) should {
            it.put("stream", listOf("line 1", "line 2").joinToString("\n").byteInputStream()) should { putFile ->
                putFile.shouldExist()
                putFile.readText() shouldBe "line 1\nline 2"
                putFile.pathString shouldBe it.get("stream")?.pathString
            }
        }
    }

    @Test
    fun put_file() = runTest {
        val file = createTempFile().also { it.appendLines(listOf("line 1", "line 2")) }
        FileCache(createTempDirectory()) should {
            it.put("file", file) should { putFile ->
                putFile.shouldExist()
                putFile.readLines().shouldContainExactly("line 1", "line 2")
                putFile.pathString shouldBe it.get("file")?.pathString
            }
        }
    }

    @Test
    fun put_directory() = runTest {
        val directory = createTempDirectory().also {
            it.resolve("file1").createFile().appendLines(listOf("line 1", "line 2"))
            it.resolve("file2").createFile().appendLines(listOf("line a", "line b"))
        }
        FileCache(createTempDirectory()) should {
            it.put("dir", directory) should { putDirectory ->
                putDirectory.shouldExist()
                putDirectory.shouldBeADirectory()
                putDirectory.shouldContainFiles("file1", "file2")
                putDirectory.resolve("file1").readLines().shouldContainExactly("line 1", "line 2")
                putDirectory.resolve("file2").readLines().shouldContainExactly("line a", "line b")
            }
        }
    }

    @Test
    fun get_or_put() = runTest {
        FileCache(createTempDirectory()) should {
            it.getOrPut("stream") { listOf("line 1", "line 2").joinToString("\n").byteInputStream() } should { putFile ->
                putFile.shouldExist()
                putFile.readText() shouldBe "line 1\nline 2"
                putFile.pathString shouldBe it.get("stream")?.pathString
            }
            it.getOrPut("stream") { fail("content already computed") } should { putFile ->
                putFile.shouldExist()
                putFile.readText() shouldBe "line 1\nline 2"
                putFile.pathString shouldBe it.get("stream")?.pathString
            }
        }
    }

    @Test
    fun get_or_collect() = runTest {
        FileCache(createTempDirectory()) should {
            it.getOrCollect("file") {
                resolve("file1").createFile().appendLines(listOf("line 1", "line 2"))
                resolve("file2").createFile().appendLines(listOf("line a", "line b"))
            } should { collected ->
                collected.shouldExist()
                collected.shouldBeADirectory()
                collected.shouldContainFiles("file1", "file2")
                collected.resolve("file1").readLines().shouldContainExactly("line 1", "line 2")
                collected.resolve("file2").readLines().shouldContainExactly("line a", "line b")
            }
            it.getOrCollect("file") { fail("content already computed") } should { collected ->
                collected.shouldExist()
                collected.shouldBeADirectory()
                collected.shouldContainFiles("file1", "file2")
                collected.resolve("file1").readLines().shouldContainExactly("line 1", "line 2")
                collected.resolve("file2").readLines().shouldContainExactly("line a", "line b")
            }

            shouldThrow<IllegalStateException> { it.getOrCollect("fails-to-create") { error("failed to create") } }
            it.get("fails-to-create").shouldBeNull()
        }
    }


    @Test
    fun file_cache3() = runTest {
        FileCache(createTempDirectory()) should {
            it.get("name") shouldBe null
            it.put("name", listOf("line 1", "line 2").joinToString("\n").byteInputStream()) should { putFile ->
                putFile.shouldExist()
                putFile.readText() shouldBe "line 1\nline 2"
                putFile.pathString shouldBe it.get("name")?.pathString
            }
            it.update("name", { fail("no need to upgrade") }) { it.age > 10.seconds } should { updatedFile ->
                updatedFile.readText() shouldBe "line 1\nline 2"
            }
            it.update("name", { "updated".byteInputStream() }) { true } should { updatedFile ->
                updatedFile.readText() shouldBe "updated"
            }
            it.remove("name") shouldBe true
            it.get("name") shouldBe null
        }
    }
}
