package com.bkahlert.netmon.identity

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.enrichment.Enricher
import com.bkahlert.netmon.logging.SLF4J

/**
 * Resolves a scanned host's name, model, vendor, kind, link and speed from the clues of [sources], asking [fallbacks]
 * only when no source named a model.
 *
 * The scanned host's own name (nmap's reverse lookup) and vendor (nmap's OUI table) enter as clues and are cleared
 * first, so they rank like every other clue. Names are read for brand tokens before placeholders are dropped.
 */
class IdentityEnricher(
    private val resolver: IdentityResolver,
    private val sources: List<ClueSource>,
    private val fallbacks: List<ClueSource> = emptyList(),
) : Enricher<Host> {

    private val logger by SLF4J

    override fun enrich(entity: Host): Host {
        val collected = sources.flatMap { it.clues(entity) }.toMutableList()
        if (collected.none { it is Clue.Model }) collected += fallbacks.flatMap { it.clues(entity) }
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

        return resolver.resolve(entity.copy(name = null, vendor = null), clues)
            .also { logger.debug("{} resolved from {} clue(s): name={}, model={}, vendor={}, kind={}", it.ip, clues.size, it.name, it.model, it.vendor, it.kind) }
    }

    override fun toString(): String = "IdentityEnricher(${sources.joinToString { it::class.simpleName ?: "source" }})"
}
