package com.bkahlert.netmon.mdns

import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.logging.logback.StructuredArguments
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class DerivedValue<V> private constructor(
    private val checksum: () -> Int,
    private val compute: () -> V
) : ReadOnlyProperty<Any?, V> {
    private val logger by SLF4J
    var cached: Pair<Int, V>? = null

    val value: V
        get() {
            val newChecksum = checksum()
            return when (val validated = cached?.takeIf { (cachedChecksum, _) -> cachedChecksum == newChecksum }) {
                null -> compute().also { cached = newChecksum to it }
                    .also { logger.info("Updated cached value: {}", StructuredArguments.v("hashCode", it.hashCode())) }

                else -> validated.second
                    .also { logger.debug("Using cached value: {}", StructuredArguments.v("hashCode", it.hashCode())) }
            }
        }

    override fun hashCode(): Int = checksum()
    override fun toString(): String = "DerivedValue(cached=$cached)"

    override fun getValue(thisRef: Any?, property: KProperty<*>): V = value

    companion object {
        fun <T, V> (() -> T).derived(checksum: (T) -> Int, compute: (T) -> V): DerivedValue<V> =
            DerivedValue({ checksum(this()) }, { compute(this()) })

        infix fun <T, V> (() -> T).derived(compute: (T) -> V): DerivedValue<V> =
            derived({ it.hashCode() }, compute)
    }
}
