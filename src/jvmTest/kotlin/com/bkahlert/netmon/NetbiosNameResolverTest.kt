package com.bkahlert.netmon

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

@Ignore
class NetbiosNameResolverTest {

    val resolver get() = NetbiosNameResolver()

    @Test
    fun resolve() {
        resolver.resolve(IP("0.0.0.1")).shouldNotBeNull()
        shouldNotThrowAny { resolver.resolve(IP("10.0.0.1")).also { println(it) } }
    }

    @Test
    fun performance() {
        measureTime {
            resolver.resolve(IP("0.0.0.1"))
        } shouldBeLessThan 1.seconds
    }
}
