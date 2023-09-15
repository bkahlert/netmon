package com.bkahlert.netmon.model_identification

import java.net.URL
import java.nio.file.Path
import kotlin.io.path.appendLines
import kotlin.io.path.createFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists

/** SF Symbols 5 vector graphics exported from [SF Symbols 5.0](https://www.figma.com/community/file/886999666531731323) */
open class SFSymbols5(
    vararg val patches: (String, String) -> String,
) : AbstractMap<String, String>() {

    private val resource: URL = checkNotNull(SFSymbols5::class.java.classLoader.getResource(RESOURCE_NAME)) { "SF Symbols 5 not found." }

    override val entries: Set<Map.Entry<String, String>> by lazy {
        buildSet {
            resource.openStream().bufferedReader()
                .readLines()
                .filterNot { it.startsWith("_") }
                .mapTo(this) { fileName ->
                    val cleanFileName = fileName.removePrefix("Icon=")
                    val symbolResource = "$RESOURCE_NAME/$fileName"
                    val name = cleanFileName.removeSuffix(".svg")
                    LazyMapEntry(name) {
                        val content = checkNotNull(SFSymbols5::class.java.classLoader.getResource(symbolResource)) {
                            "Symbol resource $symbolResource unexpectedly missing"
                        }.readText()
                        patches.fold(content) { acc, patch -> patch(name, acc) }
                    }
                }
        }
    }

    class LazyMapEntry<V>(override val key: String, initializer: () -> V) : Map.Entry<String, V> {
        override val value: V by lazy(initializer)
    }

    companion object : SFSymbols5(
        AttributesRemovalPatch(),
        ColorReplacementPatch(),
        FillRemovalPatch(),
        DefaultAttributeRemovalPatch("fill-opacity" to "0.85"),
    ) {
        private const val RESOURCE_NAME = "sfsymbols5"

        /** Creates an HTML file that shows all symbols. */
        fun dumpTo(directory: Path) = directory.resolve("sfsymbols5.html").apply {
            if (exists()) deleteExisting()
            createFile()
            appendLines(
                listOf(
                    "<!DOCTYPE html>",
                    """<html lang="en">""",
                    "<head>",
                    "<style>",
                    // language=css
                    """
                    html {
                        font-family: ui-sans-serif,system-ui,-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,Helvetica Neue,Arial,Noto Sans,sans-serif,Apple Color Emoji,Segoe UI Emoji,Segoe UI Symbol,Noto Color Emoji;
                        line-height: 1.5;
                    }

                    .symbols {
                        display: grid;
                        grid-gap: 1rem;
                        grid-template-columns: repeat(auto-fill,5rem);
                        justify-content: space-evenly;
                        border: .2rem solid currentColor;
                        border-radius: 1rem;
                        padding: 1rem;
                    }

                    .symbol::after {
                        content: attr(title);
                        display: block;
                        text-align: center;
                        text-overflow: ellipsis;
                        overflow: hidden;
                    }

                    .symbol svg {
                        width: 100%;
                        display: block;
                    }

                    .color-rotate {
                        animation: color-rotate 5s infinite linear;
                    }

                    @keyframes color-rotate {
                        0% {
                            color: oklch(69.85% 0.133 0.0);
                        }
                        25% {
                            color: oklch(69.85% 0.133 90.0);
                        }
                        50% {
                            color: oklch(69.85% 0.133 180.0);
                        }
                        75% {
                            color: oklch(69.85% 0.133 270.0);
                        }
                        100% {
                            color: oklch(69.85% 0.133 360.0);
                        }
                    }
                    """.trimIndent(),
                    "</style>",
                    "</head>",
                    "<body>",
                    "<h1>SF Symbols 5</h1>",
                )
            )

            appendLines(buildList {
                add("""<div style="display: flex; gap: 1rem;">""")

                add("""<div style="flex: 1;">""")
                add("""<h2>Unchanged</h2>""")
                add("""<div class="symbols">""")
                SFSymbols5().entries.drop(300).take(15).forEach { add("""<div class="symbol" title="${it.key}">${it.value}</div>""") }
                add("""</div>""")
                add("""</div>""")

                add("""<div style="flex: 1;">""")
                add("""<h2>black → currentColor</h2>""")
                add("""<div class="symbols" style="color: rgb(84 163 186);">""")
                SFSymbols5(ColorReplacementPatch()).entries.drop(300).take(15).forEach { add("""<div class="symbol" title="${it.key}">${it.value}</div>""") }
                add("""</div>""")
                add("""</div>""")

                add("""<div style="flex: 1;">""")
                add("""<h2>width and height removed</h2>""")
                add("""<div class="symbols" style="color: oklch(69.85% 0.271 0);">""")
                SFSymbols5(ColorReplacementPatch()).entries.drop(300).take(15).forEach { add("""<div class="symbol" title="${it.key}">${it.value}</div>""") }
                add("""</div>""")
                add("""</div>""")

                add("""</div>""")
            })

            appendLines(buildList {
                add("""<div style="flex: 1;"><h2>All symbols / all patches</h2>""")
                add("""<div class="symbols color-rotate" style="color: oklch(69.85% 0.271 0); zoom: 0.7;">""")
                SFSymbols5.forEach { add("""<div class="symbol" title="${it.key}">${it.value}</div>""") }
                add("""</div>""")
                add("""</div>""")
            })

            appendLines(listOf("</body>", "</html>"))
        }
    }
}

private fun attributeRegex(
    attribute: String,
    value: String? = null,
): Regex {
    val valuePattern = if (value == null) """[^"]*""" else Regex.fromLiteral(value).pattern
    return Regex("""\s(?<name>$attribute)="(?<value>$valuePattern)"""")
}

/** Creates a from the given [transform]. */
fun Patch(transform: (name: String, content: String) -> String): (String, String) -> String = transform

/** Removes the first occurrence of each of the specified [attributes] (default: `width` and `height`) from the given SVG content. */
fun AttributesRemovalPatch(vararg attributes: String = arrayOf("width", "height")): (String, String) -> String {
    val attributeRegexes = attributes.map { attributeRegex(it) }
    return Patch { _, content ->
        attributeRegexes.fold(content) { acc, attributeRegex ->
            acc.replaceFirst(attributeRegex, "")
        }
    }
}

/** Replaces the [oldColor] (default: `black`) with the specified [newColor] (default: `currentColor`). */
fun ColorReplacementPatch(oldColor: String = "black", newColor: String = "currentColor"): (String, String) -> String = Patch { _, content ->
    content.replace(oldColor, newColor)
}

/** Removes the `fill-opacity` attribute from the first SVG element of all symbols specified by their [names]. */
fun FillRemovalPatch(
    vararg names: String = arrayOf(
        "applewatch",
        "desktopcomputer",
        "display",
        "ipad.gen1",
        "ipad.gen2",
        "ipad",
        "iphone.gen1",
        "iphone.gen2",
        "iphone.gen3",
        "iphone",
        "ipodtouch",
        "macbook.gen1",
        "macbook.gen2",
        "macbook",
        "tv",
    )
): (String, String) -> String {
    val fillOpacityAttributeRegex = attributeRegex("fill-opacity")
    return Patch { name, content ->
        if (name in names) {
            val fillOpacityAttributes = fillOpacityAttributeRegex.findAll(content).toList()
            if (fillOpacityAttributes.size > 1) {
                val range = fillOpacityAttributes[0].range
                content.substring(0, range.first) + """ fill-opacity="0"""" + content.substring(range.last + 1)
            } else {
                content
            }
        } else {
            content
        }
    }
}

/** Removes all occurrences of attributes with the specified name and value. */
fun DefaultAttributeRemovalPatch(vararg attributes: Pair<String, String>): (String, String) -> String {
    val attributeRegexes = attributes.map { (name, value) -> attributeRegex(name, value) }
    return Patch { _, content ->
        attributeRegexes.fold(content) { acc, attributeRegex ->
            acc.replace(attributeRegex, "")
        }
    }
}
