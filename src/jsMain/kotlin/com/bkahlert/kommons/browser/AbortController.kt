@file:Suppress("RedundantVisibilityModifier")

package com.bkahlert.kommons.browser

/** A controller object that allows you to abort one or more DOM requests as and when desired. */
public external interface AbortController {
    /** Returns the AbortSignal object associated with this object. */
    public val signal: dynamic

    /** Invoking this method will set this object's AbortSignal's aborted flag and signal to any observers that the associated activity is to be aborted. */
    public fun abort(reason: Any? = definedExternally)
}
