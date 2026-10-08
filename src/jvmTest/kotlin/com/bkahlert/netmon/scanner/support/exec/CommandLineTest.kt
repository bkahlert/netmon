package com.bkahlert.netmon.scanner.support.exec

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.IOException
import kotlin.test.Test

class CommandLineTest {

    @Test
    fun output_of_a_succeeding_command() {
        val output = CommandLine("echo", "hello").exec().readTextOrThrow()
        output shouldBe "hello\n"
    }

    @Test
    fun failing_command_throws_with_exit_code_and_error_output() {
        val exit = CommandLine("sh", "-c", "echo oops >&2; exit 3").exec()
        val exception = shouldThrow<IOException> { exit.readTextOrThrow() }
        exception.message should {
            it shouldContain "exit code 3"
            it shouldContain "oops"
        }
    }

    @Test
    fun is_the_list_of_command_and_arguments() {
        val commandLine = CommandLine("nmap", listOf("--privileged", "-sn"))
        commandLine shouldContainExactly listOf("nmap", "--privileged", "-sn")
    }

    @Test
    fun string_form_quotes_arguments_with_whitespace() {
        val string = CommandLine("nmap", "-sn", "10.0.0.0/24", "a b").toString()
        string shouldBe "nmap -sn 10.0.0.0/24 'a b'"
    }
}
