package com.bkahlert.netmon.scanner.scan.enrichment

/** An enricher contributes additional information to an entity. */
fun interface Enricher<T> {
    /** Returns an enriched copy of the given [entity], or `null` if no additional information can be contributed. */
    fun enrich(entity: T): T?
}
