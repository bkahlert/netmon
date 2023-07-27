@file:Suppress("RedundantVisibilityModifier")

package com.bkahlert.kommons.js

/** Exposes the [console API](https://developer.mozilla.org/en/DOM/console) to Kotlin. */
public external interface Console {
    public fun assert(condition: Boolean? = definedExternally, vararg data: Any?)
    public fun clear()
    public fun count(label: String? = definedExternally)
    public fun countReset(label: String = definedExternally)

    /**
     * Outputs a message to this [Console] at the "debug" log level.
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/console/debug">console.debug()</a>
     */
    public fun debug(vararg data: Any?)
    public fun dir(item: Any? = definedExternally, options: Any? = definedExternally)
    public fun dirxml(vararg data: Any?)
    public fun error(vararg data: Any?)

    /**
     * Creates a new inline group in this [Console] log,
     * causing any later console messages to be indented by an extra level,
     * until [Console.groupEnd] is called.
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/console/group">console.group()</a>
     */
    public fun group(vararg data: Any?)

    /**
     * Creates a new inline group in this [Console].
     *
     * Unlike [Console.group], however, the new group is created collapsed.
     * The user needs to use the disclosure button next to it to expand it,
     * revealing the entries created in the group.
     *
     * Call [Console.groupEnd] to back out to the parent group.
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/console/groupCollapsed">console.groupCollapsed()</a>
     */
    public fun groupCollapsed(vararg data: Any?)

    /**
     * Exits the current inline group in this [Console].
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/console/groupEnd">console.groupEnd()</a>
     */
    public fun groupEnd()
    public fun info(vararg data: Any?)
    public fun log(vararg data: Any?)

    /**
     * Displays tabular [tabularData] as a table of the specified [properties].
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/console/table">console.table()</a>
     */
    public fun table(tabularData: Any? = definedExternally, properties: Array<String>? = definedExternally)

    /**
     * Starts a timer you can use to track how long an operation takes.
     *
     * You give each timer a unique name, and may have up to 10,000 timers running on a given page.
     * When you call [timeEnd] with the same name, the browser outputs the time, in milliseconds, which elapsed since the timer was started.
     *
     * @param label A string representing the name to give the new timer.
     * Use the same name when calling [timeEnd] to stop the timer and get the time output to the console.
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/console/time">console.time()</a>
     */
    public fun time(label: String? = definedExternally)

    /**
     * Stops a timer that was started before by calling [time].
     *
     * See [Timers](https://developer.mozilla.org/en-US/docs/Web/API/console#timers) in the documentation for details and examples.
     *
     * @param label A string representing the name of the timer to stop.
     * Once stopped, the elapsed time is automatically displayed in the Web console along with an indicator that the time has ended.
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/console/timeEnd">console.timeEnd()</a>
     */
    public fun timeEnd(label: String? = definedExternally)

    /**
     * Logs the current value of a timer that was started before by calling [time].
     *
     * @param label The name of the timer to log to the console. If this is omitted the label "default" is used.
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/console/timeLog">console.timeLog()</a>
     */
    public fun timeLog(label: String? = definedExternally, vararg data: Any?)

    /**
     * The console.timeStamp method adds a single marker to the browser's Performance tool (Firefox, Chrome). This lets you correlate a point in your code with the other events recorded in the timeline, such as layout and paint events.
     *
     * @param label Label for the timestamp. Optional.
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/console/timeStamp">console.time()</a>
     */
    @Deprecated("Non-standard, should not be used in production")
    public fun timeStamp(label: String? = definedExternally)

    /**
     * Outputs a stack trace to this [Console].
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/console/trace">console.trace()</a>
     */
    public fun trace(vararg data: Any?)
    public fun warn(vararg data: Any?)
}

/** Exposes the [console API](https://developer.mozilla.org/en/DOM/console) to Kotlin. */
public external val console: Console


/* TRACE */
public inline val <T> T.trace: T get() = this.trace()

public inline fun <T> T.trace(caption: String? = null, transform: (T) -> Any? = { it }): T {
    repeat(100) { console.groupEnd() }
    val meta = this?.let { it::class.simpleName + "@" + hashCode() } ?: "null"
    console.info(
        "%cTRACE${caption?.let { ": $it" } ?: ""}\n$meta%o",
        listOf(
            "display:inline-block",
            "border-radius:4px",
            "padding:0.1em",
            "margin-right:0.15em",
            "text-shadow:0 0 0.5px #ffffff99",
            "border:1px solid #29aae2",
            "margin-left:-10rem",
            "padding-left:10rem",
            "background-color:cyan",
            "color:black",
            "font-weight:700",
        ).joinToString(";"),
        transform(this).let { if (it is Iterable<*>) it.toList().toTypedArray() else it },
    )
    return this
}

/* EXTENSIONS */

/**
 * Creates a new—optionally [collapsed]—inline group in this [Console] log,
 * causing any later console messages to be indented by an extra level,
 * until [Console.groupEnd] is called.
 *
 * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/console/group">console.group()</a>
 */
public inline fun Console.group(collapsed: Boolean) {
    if (collapsed) groupCollapsed() else group()
}

/**
 * Creates a new—optionally [collapsed]—inline group with the specified [label] in this [Console] log,
 * causing any later console messages to be indented by an extra level,
 * until [Console.groupEnd] is called.
 *
 * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/console/group">console.group()</a>
 */
public inline fun Console.group(label: String, collapsed: Boolean) {
    if (collapsed) groupCollapsed(label) else group(label)
}
