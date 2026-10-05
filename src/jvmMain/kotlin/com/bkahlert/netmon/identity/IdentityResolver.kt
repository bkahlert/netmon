package com.bkahlert.netmon.identity

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.Kind

/** Fills each empty field of a host with the first clue in that field's source order; within a source, the first clue wins. */
class IdentityResolver {

    fun resolve(host: Host, clues: List<Clue>): Host = host.copy(
        name = host.name ?: first<Clue.Name>(clues, NAME_ORDER)?.value,
        model = host.model ?: first<Clue.Model>(clues, MODEL_ORDER)?.value,
        vendor = host.vendor ?: first<Clue.Vendor>(clues, VENDOR_ORDER)?.value,
        kind = host.kind ?: first<Clue.DeviceKind>(clues, KIND_ORDER)?.kind ?: Kind.GENERIC,
        link = host.link ?: first<Clue.Attachment>(clues, ROUTER_ONLY)?.link,
        speed = host.speed ?: first<Clue.Speed>(clues, ROUTER_ONLY)?.speed,
        mac = host.mac ?: first<Clue.Mac>(clues, ROUTER_ONLY)?.value,
    )

    private inline fun <reified C : Clue> first(clues: List<Clue>, order: List<Source>): C? {
        val candidates = clues.filterIsInstance<C>()
        return order.firstNotNullOfOrNull { source -> candidates.firstOrNull { it.source == source } }
    }

    companion object {
        val NAME_ORDER = listOf(Source.USER, Source.PROTOCOL, Source.MDNS_HOST, Source.DNS, Source.ROUTER)
        val MODEL_ORDER = listOf(Source.PROTOCOL, Source.APPLE_CODE)
        val VENDOR_ORDER = listOf(Source.NAME_TOKEN, Source.OUI, Source.PROTOCOL, Source.APPLE_CODE)
        val KIND_ORDER = listOf(Source.USER, Source.APPLE_CODE, Source.PROTOCOL, Source.NAME_TOKEN, Source.ROUTER, Source.OUI)
        val ROUTER_ONLY = listOf(Source.ROUTER)
    }
}
