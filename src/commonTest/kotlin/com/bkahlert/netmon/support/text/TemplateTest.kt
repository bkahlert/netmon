package com.bkahlert.netmon.support.text

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class TemplateTest {

    @Test
    fun fields() = runTest {
        forAll(
            row("a template", emptySet()),
            row("a template with a \${foo}", setOf("foo")),
            row("a template with a \${foo} and a \${bar}", setOf("foo", "bar")),
        ) { text, expected ->
            Template(text).fields shouldContainExactly expected
        }
    }

    @Test
    fun to_string() = runTest {
        forAll(
            row("a template"),
            row("a template with a \${foo}"),
            row("a template with a \${foo} and a \${bar}"),
        ) { text ->
            Template(text).toString() shouldBe text
        }
    }

    @Test
    fun to_string_with_substitutions() = runTest {
        val template = Template("a template with a \${foo} and a \${bar}")
        template.toString(mapOf("foo" to "FOO", "bar" to "BAR")) shouldBe "a template with a FOO and a BAR"
    }

    @Test
    fun to_string_with_missing_substitutions() = runTest {
        val template = Template("a template with a \${foo} and a \${bar}")
        shouldThrow<IllegalArgumentException> { template.toString(mapOf("bar" to "BAR")) }
            .message shouldBe "Missing substitutions: [foo]"
    }

    @Test
    fun to_string_with_extra_substitutions() = runTest {
        val template = Template("a template with a \${foo} and a \${bar}")
        shouldThrow<IllegalArgumentException> { template.toString(mapOf("foo" to "FOO", "bar" to "BAR", "baz" to "BAZ")) }
            .message shouldBe "Extra substitutions: [baz]"
    }
}
