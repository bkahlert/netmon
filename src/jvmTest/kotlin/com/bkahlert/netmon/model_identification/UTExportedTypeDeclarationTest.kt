package com.bkahlert.netmon.model_identification

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class UTExportedTypeDeclarationTest

private val _PublicItemType: UTExportedTypeDeclaration = UTExportedTypeDeclaration(
    iconFiles = listOf(
        "generic_20x20.png",
        "generic_20x20@2x.png",
        "generic_145x145.png",
        "generic_145x145@2x.png",
    ),
    identifier = "public.item",
    description = "item",
    isWildcard = true,
)

val _PublicDataType: UTExportedTypeDeclaration = UTExportedTypeDeclaration(
    icons = UTTypeIcons(
        symbolName = "doc",
        isIconRecipe = JsonObject(mapOf("class-name" to JsonPrimitive("ISmacosDocumentRecipe1016"))),
        iconFile = "GenericDocumentIcon.icns",
        templateIconFile = "SidebarGenericFile.icns",
    ),
    conformsTo = listOf("public.item"),
    iconFiles = listOf("-"),
    identifier = "public.data",
    description = "data",
    isWildcard = true,
    tagSpecification = UTTypeTagSpecification(
        osTypes = listOf("sbFl"),
        mimeTypes = listOf("application/octet-stream"),
    ),
)


val UTExportedTypeDeclaration.Companion.PublicItemType: UTExportedTypeDeclaration get() = _PublicItemType
val UTExportedTypeDeclaration.Companion.PublicDataType: UTExportedTypeDeclaration get() = _PublicDataType
