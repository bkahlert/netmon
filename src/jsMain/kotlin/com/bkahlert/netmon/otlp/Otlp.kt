package com.bkahlert.netmon.otlp

import kotlinx.serialization.Serializable

/** The part of an OTLP/JSON `MetricsData`, the body of `/v1/metrics`, the display reads. */
@Serializable
data class MetricsData(val resourceMetrics: List<ResourceMetrics> = emptyList())

/** The metrics of one entity, the [resource]. */
@Serializable
data class ResourceMetrics(val resource: Resource = Resource(), val scopeMetrics: List<ScopeMetrics> = emptyList()) {
    /** Returns the metric called [name], or `null` if there is none. */
    fun metric(name: String): Metric? = scopeMetrics.flatMap { it.metrics }.firstOrNull { it.name == name }
}

/** An entity, described by its attributes. */
@Serializable
data class Resource(val attributes: List<KeyValue> = emptyList()) {
    /** Returns the value of the attribute [key], or `null` if there is none. */
    operator fun get(key: String): String? = attributes.valueOf(key)
}

/** The metrics of one instrumentation scope. */
@Serializable
data class ScopeMetrics(val metrics: List<Metric> = emptyList())

/** A metric, either a [gauge] or a [sum]; a semconv updowncounter is a [sum]. */
@Serializable
data class Metric(val name: String, val unit: String = "", val gauge: Points? = null, val sum: Points? = null) {
    /** The data points of whichever of [gauge] and [sum] is set. */
    val dataPoints: List<NumberDataPoint> get() = gauge?.dataPoints ?: sum?.dataPoints.orEmpty()
}

/** The data points of a gauge or a sum. */
@Serializable
data class Points(val dataPoints: List<NumberDataPoint> = emptyList())

/** A number at [timeUnixNano], in nanoseconds since the epoch as a decimal string. */
@Serializable
data class NumberDataPoint(
    val attributes: List<KeyValue> = emptyList(),
    val timeUnixNano: String = "0",
    val asDouble: Double? = null,
    val asInt: String? = null,
) {
    /** The point's value, whichever of [asDouble] and [asInt] it carries, or `null` if it carries neither. */
    val value: Double? get() = asDouble ?: asInt?.toDoubleOrNull()

    /** Returns the value of the attribute [key], or `null` if there is none. */
    operator fun get(key: String): String? = attributes.valueOf(key)
}

/** An attribute. */
@Serializable
data class KeyValue(val key: String, val value: AnyValue = AnyValue())

/** An attribute value; OTLP/JSON writes 64-bit integers as strings. */
@Serializable
data class AnyValue(val stringValue: String? = null, val intValue: String? = null)

private fun List<KeyValue>.valueOf(key: String): String? = firstOrNull { it.key == key }?.value?.let { it.stringValue ?: it.intValue }
