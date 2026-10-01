package com.bkahlert.netmon.model_identification

import com.bkahlert.kommons.FileCache
import com.bkahlert.kommons.io.useBufferedOutputStream
import com.bkahlert.netmon.logging.SLF4J
import net.logstash.logback.argument.StructuredArguments.v
import com.bkahlert.netmon.serialization.DataUrl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import java.nio.charset.Charset
import java.nio.file.Path
import kotlin.io.encoding.Base64
import kotlin.io.path.appendLines
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.fileSize
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.io.path.readBytes
import kotlin.io.path.readText

/** Utility to create and export [DeviceModelCodes] instances. */
object DeviceModelCodesExporter {

    private val logger by SLF4J

    /**
     * Creates a [DeviceModelCodes] instance from the given [types],
     * and export it, and accompanied by the referenced icons to the specified [directory].
     *
     * If a [cache] is given, the icons are created with it.
     * Cached icons won't be created again, which can speed up the export significantly—at the cost
     * of icon changes not being reflected.
     *
     * The directory will contain:
     * - `device-model-codes.external-assets.json` - a JSON file containing the [DeviceModelCodes] with the contained icons being file names
     * - `device-model-codes.external-assets.html` - an HTML file showing all contained [DeviceModelCodes] and icons
     * - `device-model-codes.embedded-assets.json` - a JSON file containing the [DeviceModelCodes] with the contained icons being data URLs
     * - `device-model-codes.embedded-assets.html` - an HTML file showing all contained [DeviceModelCodes] and icons
     * - all referenced icons, for example `SidebarFooProCylinder.png`
     */
    fun exportTo(
        types: Types,
        directory: Path,
        cache: FileCache? = null,
    ): DeviceModelCodes {
        require(directory.isDirectory()) { "Assets directory $directory must be a directory." }

        val deviceModelCodeToIdentifier: Map<String, String> = buildMap {
            types.keys
                .forEach { identifier ->
                    val conformingTypes = types.conformingTypes(identifier)
                    conformingTypes.first().tagSpecification?.deviceModelCodes?.forEach { deviceModelCode ->
                        this@buildMap.compute(deviceModelCode) { _, commonType ->
                            if (commonType == null) identifier
                            else conformingTypes.firstOrNull { it.identifier == commonType }?.identifier
                        }
                    }
                }
        }

        val identifiers: List<String> = deviceModelCodeToIdentifier.values.distinct()

        val temp = directory.resolve("_temp").createDirectory()
        val iconCache = cache ?: FileCache(temp.resolve("cache"))

        val iconSymbols by lazy {
            temp.resolve("symbols").createDirectory().also { directory ->
                types.allIcons(directory, iconCache = iconCache) { it.firstOrNull { it is Icon.Symbol } }
            }
        }

        val iconImageTemplates by lazy {
            temp.resolve("image-templates").createDirectory().also { directory ->
                types.allIcons(directory, iconCache = iconCache) { it.firstOrNull { it is Icon.IconImageTemplate } }

            }
        }

        val iconImages by lazy {
            temp.resolve("images").createDirectory().also { directory ->
                types.allIcons(directory, iconCache = iconCache) { it.firstOrNull { it is Icon.IconImage } }
            }
        }


        val identifierToDescription: Map<String, String?> = identifiers.associateWith { identifier ->
            types.description(identifier).also {
                if (it == null) logger.warn("No description found for {}", v("identifier", identifier))
                else logger.debug("Description found for {}: {}", v("identifier", identifier), v("description", it))
            }
        }

        val identifierToIcon: Map<String, String?> = identifiers.associateWith { identifier ->
            val icon = iconSymbols.resolve(identifier).takeIf { it.exists() }?.toRealPath()?.let { it.fileName.pathString to it }
                ?: iconImageTemplates.resolve(identifier).takeIf { it.exists() }?.toRealPath()?.listDirectoryEntries()?.maxBy { it.fileSize() }
                    ?.let { it.parent.fileName.pathString.substringBeforeLast(".").plus(".").plus(it.extension) to it }
                ?: iconImages.resolve(identifier).takeIf { it.exists() }?.toRealPath()?.listDirectoryEntries()?.maxBy { it.fileSize() }
                    ?.let { it.parent.fileName.pathString.substringBeforeLast(".").plus(".").plus(it.extension) to it }

            if (icon == null) {
                logger.warn("No icon found for {}", v("identifier", identifier))
                null
            } else {
                val (name, file) = icon
                logger.debug("Icon found for {}: {}", v("identifier", identifier), v("icon", name))
                val assetFile = directory.resolve(name)
                if (!assetFile.exists()) {
                    file.copyTo(assetFile)
                }
                name
            }
        }

        if (temp.exists()) kotlin.run { temp.deleteRecursively() }

        val deviceModelCodes = DeviceModelCodes(
            deviceModelCodeToIdentifier = deviceModelCodeToIdentifier,
            identifierDescriptionMappings = identifierToDescription,
            identifierIconMappings = identifierToIcon.mapKeys { it.key },
        )

        mapOf(
            "device-model-codes.external-assets.json" to deviceModelCodes,
            "device-model-codes.embedded-assets.json" to deviceModelCodes.copy(
                embeddedIcons = identifierToIcon.values.filterNotNull().associateWith { name ->
                    directory.resolve(name).takeIf { it.exists() }?.toDataUrl()
                }
            ),
        ).forEach { (fileName, instance) ->
            directory.resolve(fileName).apply {
                useBufferedOutputStream { Json.encodeToStream(instance, it) }
                logger.info("Created {}", v("file", pathString))
                exportHtmlTo(deviceModelCodes = instance, file = resolveSibling(fileName.substringBeforeLast(".").plus(".html"))).also {
                    logger.info("Created {}", v("file", it.pathString))
                }
            }
        }

        return directory.resolve("device-model-codes.embedded-assets.json").readText()
            .let { Json.decodeFromString(it) }
    }

    /** Creates an HTML file that shows all [deviceModelCodes]. */
    fun exportHtmlTo(deviceModelCodes: DeviceModelCodes, file: Path): Path = file.apply {
        if (exists()) deleteExisting()
        createFile()
        appendLines(
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

                table { border-collapse: collapse; border-spacing: 0; }
                td,th { padding: .5rem; line-height: 1.15; }
                thead th { text-align: left; font-weight: bold; }
                thead tr:last-child { border-bottom: 2px solid rgb(0 0 0 / 0.85); }
                tr + tr { border-top: 1px solid rgb(0 0 0 / 0.85);}
                tbody tr:nth-child(odd) { background-color: rgb(0 0 0 / 0.05); }
                td:has(img,svg) { text-align: center; max-width: 6rem; line-height: .5rem; }
                td:has(img,svg) small { font-size: .55rem; overflow-wrap: break-word; }

                img, svg { width: 100%; display: block; }
                svg { animation: color-rotate 50s infinite linear; }

                @keyframes color-rotate {
                    0% { color: oklch(69.85% 0.133 0.0); }
                    25% { color: oklch(69.85% 0.133 90.0); }
                    50% { color: oklch(69.85% 0.133 180.0); }
                    75% { color: oklch(69.85% 0.133 270.0); }
                    100% { color: oklch(69.85% 0.133 360.0); }
                }
                """.trimIndent(),
            "</style>",
            "</head>",
            "<body>",
            "<h1>Device Model Codes</h1>",
        )

        appendLines(
            "<table>",
            "<thead>",
            "<tr>",
            "<th>Device Mode Codes</th>",
            "<th>Type Identifier</th>",
            "<th>Description</th>",
            "<th>Icon</th>",
            "<th>Inlined</th>",
            "</tr>",
            "</thead>",
            "<tbody>",
        )

        val identifiers: Map<String?, List<String>> = deviceModelCodes.groupBy { deviceModelCodes.identifier(it) }
        identifiers.forEach { (identifier, codes) ->
            appendLines(buildList {
                add("""<tr>""")
                add("""<td>${codes.joinToString("<br>")}</td>""")
                add("""<td>${identifier}</td>""")
                val description = deviceModelCodes.description(codes.first())
                val extra = description?.substringAfter(" (", "")?.takeIf { it.isNotBlank() }?.let { " ($it" }
                val descriptionLines = listOfNotNull(
                    description?.removeSuffix(extra.orEmpty()),
                    extra?.let { "<small>$it</small>" },
                )
                add("""<td>${descriptionLines.joinToString("<br>")}</td>""")
                val icon = deviceModelCodes.icon(codes.first())
                if (icon != null) {
                    add("""<td><img src="${icon.source}" title="${icon.name}" /><br><small>${icon.name}</small></td>""")
                    when (val dataUrl = icon.source
                        .takeIf { it.startsWith("data:") }
                        ?.let { DataUrl(it) }
                        ?.takeIf { it.mediaType == "image/svg+xml" }) {
                        null -> add("""<td><small><em>SVG only</em></small></td>""")
                        else -> add("""<td>${Base64.decode(dataUrl.data).decodeToString()}<br><small>${icon.name}</small></td>""")
                    }
                } else {
                    add("""<td colspan="2"><small><em>no image</em></small></td>""")
                }
                add("""</tr>""")
            })
        }

        appendLines(
            "</tbody>",
            "</table>",
            "</body>",
            "</html>",
        )
    }
}

@Suppress("RedundantVisibilityModifier", "NOTHING_TO_INLINE")
public inline fun Path.appendLines(vararg lines: CharSequence, charset: Charset = Charsets.UTF_8): Path = appendLines(lines.asIterable(), charset)

fun Path.toDataUrl(): DataUrl = when (val extension = extension) {
    "svg" -> DataUrl("image/svg+xml", readBytes())
    "png" -> DataUrl("image/png", readBytes())
    "" -> throw IllegalArgumentException("Missing file extension")
    else -> throw IllegalArgumentException("Unsupported file extension: $extension")
}
