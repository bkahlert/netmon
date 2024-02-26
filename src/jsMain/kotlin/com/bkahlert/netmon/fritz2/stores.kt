@file:Suppress("RedundantVisibilityModifier")

package com.bkahlert.netmon.fritz2

import dev.fritz2.core.Lens
import dev.fritz2.core.Store
import dev.fritz2.core.lensOf


/**
 * Returns a pair of stores where *first* [Store] contains elements for which [predicate] yields `true`,
 * while *second* [Store] contains elements for which [predicate] yields `false`.
 *
 * The pair of stores is double-bound with the original store with its elements being
 * the concatenation of the elements of the *first* and *second* returned store, where
 * - only elements of the *first* store passing the predicate, and
 * - only elements of the *second* store not passing the predicate are included.
 *
 * @see Iterable.partition
 */
public fun <T> Store<List<T>>.partition(
    predicate: (T) -> Boolean
): Pair<Store<List<T>>, Store<List<T>>> {
    val map = map(
        lensOf(
            id = "hosts",
            getter = { it.partition(predicate) },
            setter = { _, (first, second) -> first.filter(predicate) + second.filterNot(predicate) },
        )
    )
    return map.map(lensForFirst()) to map.map(lensForSecond())
}


private object LensForFirst : Lens<Pair<Any?, Any?>, Any?> {
    override val id: String = "first"
    override fun get(parent: Pair<Any?, Any?>): Any? = parent.first
    override fun set(parent: Pair<Any?, Any?>, value: Any?): Pair<Any?, Any?> = value to parent.second
}

private object LensForSecond : Lens<Pair<Any?, Any?>, Any?> {
    override val id: String = "second"
    override fun get(parent: Pair<Any?, Any?>): Any? = parent.second
    override fun set(parent: Pair<Any?, Any?>, value: Any?): Pair<Any?, Any?> = parent.first to value
}

@Suppress("UNCHECKED_CAST")
private fun <A, B> lensForFirst(): Lens<Pair<A, B>, A> = LensForFirst as Lens<Pair<A, B>, A>

@Suppress("UNCHECKED_CAST")
private fun <A, B> lensForSecond(): Lens<Pair<A, B>, B> = LensForSecond as Lens<Pair<A, B>, B>
