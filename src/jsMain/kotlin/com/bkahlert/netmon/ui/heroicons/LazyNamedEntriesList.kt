@file:Suppress("RedundantVisibilityModifier")

package com.bkahlert.netmon.ui.heroicons

import kotlin.reflect.KProperty

/**
 * Utility to build lists where each entry
 * - can also be accessed by a property, and
 * - is lazily instantiated.
 *
 * Entries are specified using `by` and an instance of [T].
 *
 * The actual instance is lazily created on first access using [build], provided
 * with the [LazyNamedEntriesList] instance, the [KProperty] and the [T] instance.
 *
 * **Example:**
 * ```kotlin
 * class MyList : LazyNamedEntriesList<String, Int>({ it.length }) {
 * }
 * ```
 */
public open class LazyNamedEntriesList<T, R>(private val build: (LazyNamedEntriesList<T, R>, KProperty<*>, T) -> R) : AbstractList<R>() {
    private val entries: MutableList<Lazy<R>> = mutableListOf()
    protected operator fun T.provideDelegate(thisRef: Any?, property: KProperty<*>): Lazy<R> =
        lazy { build(this@LazyNamedEntriesList, property, this) }.also { entries.add(it) }

    override val size: Int get() = entries.size
    override fun get(index: Int): R = entries[index].value
}
