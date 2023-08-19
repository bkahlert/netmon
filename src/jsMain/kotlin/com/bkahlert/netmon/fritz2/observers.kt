package com.bkahlert.netmon.fritz2

import dev.fritz2.core.WithDomNode
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.w3c.dom.Element
import org.w3c.dom.MutationObserver
import org.w3c.dom.MutationObserverInit
import org.w3c.dom.MutationRecord

/** Flow of [MutationRecord], that emits every time any of the specified [mutations]. */
public fun WithDomNode<Element>.observedMutations(mutations: MutationObserverInit): Flow<Array<MutationRecord>> =
    domNode.observedMutations(mutations)


/** Flow of [MutationRecord], that emits every time any of the specified [mutations]. */
public fun Element.observedMutations(mutations: MutationObserverInit): Flow<Array<MutationRecord>> =
    callbackFlow {
        val observer = MutationObserver { records, _ ->
            trySend(records)
                .onFailure { ex -> console.warn("Failed to observe mutations", records, ex) }
        }

        observer.observe(this@observedMutations, mutations)

        awaitClose {
            observer.disconnect()
        }
    }
