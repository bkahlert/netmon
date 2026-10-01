package com.bkahlert.netmon.ui

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.js.Date
import kotlin.js.dateLocaleOptions
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit.DAYS
import kotlin.time.DurationUnit.HOURS
import kotlin.time.DurationUnit.MINUTES
import kotlin.time.DurationUnit.SECONDS

/**
 * Describes this duration relative to now the way a person would, e.g. "5m ago" or "in 2h 30m";
 * durations of 30 days or more are described by the date they point to.
 *
 * Set [descriptive] to `false` to drop "ago" and "in" and use a sign instead.
 */
fun Duration.toMomentString(descriptive: Boolean = true): String {
    val absDiff = absoluteValue
    return when {
        absDiff < .5.seconds -> "now"
        absDiff < 1.minutes -> describeMoment(absDiff.toString(SECONDS), descriptive)
        absDiff < 1.hours -> describeMoment(absDiff.toString(MINUTES), descriptive)
        absDiff < 6.hours -> describeMoment(buildString {
            val durationInHours = absDiff.inWholeHours.hours
            append(durationInHours.toString(HOURS))
            append(" ")
            append((absDiff - durationInHours).toString(MINUTES))
        }.removeSuffix(" 0m"), descriptive)

        absDiff < 23.5.hours -> describeMoment(absDiff.toString(HOURS), descriptive)
        absDiff < 1.days -> describeMoment("1d", descriptive)
        absDiff < 6.days -> describeMoment(buildString {
            val durationInDays = absDiff.inWholeDays.days
            append(durationInDays.toString(DAYS))
            append(" ")
            append((absDiff - durationInDays).toString(HOURS))
        }.removeSuffix(" 0h"), descriptive)

        absDiff < 30.days -> describeMoment(absDiff.toString(DAYS), descriptive)
        else -> (Clock.System.now() + this).toLocalDateString()
    }
}

private fun Duration.describeMoment(moment: String, descriptive: Boolean): String = when {
    isPositive() -> if (descriptive) "in $moment" else moment
    isNegative() -> if (descriptive) "$moment ago" else "-$moment"
    else -> moment
}

private fun Instant.toLocalDateString(): String =
    Date(toEpochMilliseconds().toDouble()).toLocaleDateString(options = dateLocaleOptions { year = "numeric"; month = "short"; day = "numeric" })
