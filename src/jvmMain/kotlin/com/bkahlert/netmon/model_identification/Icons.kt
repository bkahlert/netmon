package com.bkahlert.netmon.model_identification

import com.bkahlert.kommons.exec.CommandLine
import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.logging.logback.StructuredArguments.kv
import com.bkahlert.kommons.text.withPrefix
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectory
import kotlin.io.path.outputStream
import kotlin.io.path.pathString

@JvmInline
value class Icons(val icons: List<Icon>) : List<Icon> by icons {
    constructor(vararg icons: Icon) : this(icons.asList())
    constructor(init: MutableList<Icon>.() -> Unit) : this(buildList(init))

    fun toIconSets(
        path: Path,
        bundles: List<Bundle> = CoreTypes().bundle.allBundles,
    ): Map<String, Path?> = path.let {
        icons.associate { icon ->
            icon.name to kotlin.runCatching { icon.toIconSet(path, bundles) }
                .onFailure { logger.warn("Iconset creation failed: {}, {}", kv("icon", icon), kv("cause", it.message)) }
                .getOrNull()
        }
    }

    companion object {
        private val logger by SLF4J
        fun of(type: UTExportedTypeDeclaration) = Icons {
            type.iconFile?.also { add(Icon.IconImage(it)) }
            type.iconFiles?.takeUnless { it.size == 1 && it.first() == "-" }?.also { add(Icon.IconFiles(it)) }
            type.icons?.also { icons ->
                icons.iconFile?.also { add(Icon.IconImage(it)) }
                icons.templateIconFile?.also { add(Icon.IconImageTemplate(it)) }
                icons.symbolName?.also { add(Icon.Symbol(it)) }
            }
        }
    }
}

sealed interface Icon {

    /** The name of the image resource. */
    val name: String

    /** Searches the icon in the specified [bundles] and exports it as icon set to the specified [path]. */
    fun toIconSet(path: Path, bundles: List<Bundle> = CoreTypes().bundle.allBundles): Path

    /** An [Icon] based on a set of image files. */
    data class IconFiles(
        override val name: String,
        val variants: List<String>,
        val extension: String,
    ) : Icon {
        private val logger by SLF4J

        constructor(files: List<String>) : this(
            name = files.map { it.substringBeforeLast('_') }.distinct().let {
                if (it.size == 1) it.first() else error("Multiple icon files found: $it")
            },
            variants = files.map { it.substringAfterLast('_').substringBeforeLast('.') },
            extension = files.map { it.substringAfterLast('.') }.distinct().let {
                if (it.size == 1) it.first() else error("Multiple icon file extensions found: $it")
            },
        )

        override fun toIconSet(path: Path, bundles: List<Bundle>): Path = path.resolve("$name.iconset").also { iconset ->
            val filePaths = variants.mapNotNull { variant ->
                val file = "${name}_$variant.$extension"
                bundles.firstNotNullOfOrNull { it.iconPath(file) }
                    .also { if (it == null) logger.warn("File not found: {}", kv("file", file)) }
            }
            require(filePaths.isNotEmpty()) { "Icon ${toString()} not found" }
            iconset.createDirectory()
            filePaths.forEach { filePath ->
                filePath.copyTo(iconset.resolve(filePath.fileName.pathString.removePrefix("${name}_").withPrefix("icon_")))
            }
        }

        override fun toString(): String = "icon:files:${name}_{${variants.joinToString(",")}}.$extension"
    }

    /** An [Icon] based on an `icns` file. */
    data class IconImage(
        override val name: String,
        val extension: String,
    ) : Icon {
        constructor(file: String) : this(
            name = file.substringBeforeLast('.'),
            extension = file.substringAfterLast('.')
        )

        init {
            check(extension == "icns") { "Icon $name is not an icns but an $extension file." }
        }

        override fun toIconSet(path: Path, bundles: List<Bundle>): Path = path.resolve("$name.iconset").also { iconset ->
            val file = "$name.${extension}"
            val filePath = requireNotNull(bundles.firstNotNullOfOrNull { it.iconPath(file) }) { "Icon $file not found" }
            CommandLine(
                "iconutil",
                "--convert",
                "iconset",
                "--output",
                iconset.pathString,
                filePath.pathString,
            ).exec().readBytesOrThrow()
        }

        override fun toString(): String = "icon:file:$name.$extension"
    }

    /** A template [Icon] based on an `icns` file. */
    data class IconImageTemplate(
        override val name: String,
        val extension: String,
    ) : Icon by IconImage(name, extension) {
        constructor(file: String) : this(
            name = file.substringBeforeLast('.'),
            extension = file.substringAfterLast('.')
        )

        override fun toString(): String = "icon:template:$name.$extension"
    }

    /** An [Icon] based on [SF Symbols](https://developer.apple.com/sf-symbols/). */
    data class Symbol(override val name: String) : Icon {

        override fun toIconSet(path: Path, bundles: List<Bundle>): Path {
            val content = requireNotNull(SFSymbols5[name]) { "Icon $name not found" }
            val fileName = "$name.svg"
            return path.resolve(fileName).also {
                it.outputStream().write(content.toByteArray())
            }
        }

        override fun toString(): String = "symbol:$name"

    }
}
