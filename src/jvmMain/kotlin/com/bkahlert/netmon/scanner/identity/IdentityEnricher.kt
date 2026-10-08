package com.bkahlert.netmon.scanner.identity

import com.bkahlert.netmon.contract.Host
import com.bkahlert.netmon.contract.Kind
import com.bkahlert.netmon.scanner.scan.enrichment.Enricher
import com.bkahlert.netmon.scanner.support.logging.SLF4J

/**
 * Resolves a scanned host's identity from router, mDNS, SSDP and OUI clues in that fixed order, asking
 * [lockdownClues] only when no primary source named a model.
 *
 * The scanned host's reverse name and OUI vendor are considered alongside other clues. Names are read for brand
 * tokens before placeholders are dropped.
 *
 * @param routerClues Router-provided user and automatic host-table clues.
 * @param mdnsClues Host names and device identity clues from mDNS.
 * @param ssdpClues Device identity clues from SSDP descriptions.
 * @param ouiClues Vendor clues from the scanned MAC prefix.
 * @param lockdownClues Lockdown model clues used only when the primary sources provide no model.
 * @property lockdownClues The optional lockdown clue source.
 */
class IdentityEnricher(
    routerClues: ClueSource,
    mdnsClues: ClueSource,
    ssdpClues: ClueSource,
    ouiClues: ClueSource,
    private val lockdownClues: ClueSource? = null,
) : Enricher<Host> {

    private val logger by SLF4J
    private val sources = listOf(routerClues, mdnsClues, ssdpClues, ouiClues)

    override fun enrich(entity: Host): Host {
        val collected = sources.flatMap { it.clues(entity) }.toMutableList()
        if (collected.none { it is Clue.Model }) lockdownClues?.let { collected += it.clues(entity) }
        entity.name?.let { collected += Clue.Name(it, Source.DNS) }

        val names = collected.filterIsInstance<Clue.Name>()
        val tokens = names.flatMap { name -> NameTokens.matches(name.value) }.flatMap { token ->
            listOfNotNull(
                token.vendor?.let { Clue.Vendor(it, Source.NAME_TOKEN) },
                token.kind?.let { Clue.DeviceKind(it, Source.NAME_TOKEN) },
            )
        }
        val cleanNames = names.mapNotNull { name -> Placeholders.clean(name.value)?.let { name.copy(value = it) } }
        val clues = collected.filterNot { it is Clue.Name } + cleanNames + tokens

        return entity.copy(
            name = first<Clue.Name>(clues, NAME_ORDER)?.value,
            model = entity.model ?: first<Clue.Model>(clues, MODEL_ORDER)?.value,
            vendor = first<Clue.Vendor>(clues, VENDOR_ORDER)?.value,
            kind = entity.kind ?: first<Clue.DeviceKind>(clues, KIND_ORDER)?.kind ?: Kind.GENERIC,
            link = entity.link ?: first<Clue.Attachment>(clues, ROUTER_ONLY)?.link,
            speed = entity.speed ?: first<Clue.Speed>(clues, ROUTER_ONLY)?.speed,
            mac = entity.mac ?: first<Clue.Mac>(clues, ROUTER_ONLY)?.value,
        )
            .also {
                logger.debug(
                    "{} resolved from {} clue(s): name={}, model={}, vendor={}, kind={}",
                    it.ip,
                    clues.size,
                    it.name,
                    it.model,
                    it.vendor,
                    it.kind,
                )
            }
    }

    override fun toString(): String = "IdentityEnricher(${sources.joinToString { it::class.simpleName ?: "source" }})"

    /**
     * Selects the first clue in the supplied field-specific trust order.
     *
     * @param clues All clues collected for the host.
     * @param order Sources ordered by precedence for one field.
     */
    private inline fun <reified C : Clue> first(clues: List<Clue>, order: List<Source>): C? {
        val candidates = clues.filterIsInstance<C>()
        for (source in order) {
            candidates.firstOrNull { it.source == source }?.let { return it }
        }
        return null
    }

    private companion object {
        val NAME_ORDER = listOf(Source.USER, Source.PROTOCOL, Source.MDNS_HOST, Source.DNS, Source.ROUTER)
        val MODEL_ORDER = listOf(Source.PROTOCOL, Source.APPLE_CODE)
        val VENDOR_ORDER = listOf(Source.NAME_TOKEN, Source.OUI, Source.PROTOCOL, Source.APPLE_CODE)
        val KIND_ORDER = listOf(Source.USER, Source.APPLE_CODE, Source.PROTOCOL, Source.NAME_TOKEN, Source.ROUTER, Source.OUI)
        val ROUTER_ONLY = listOf(Source.ROUTER)
    }
}
