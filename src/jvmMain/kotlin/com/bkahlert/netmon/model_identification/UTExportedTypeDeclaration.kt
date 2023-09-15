package com.bkahlert.netmon.model_identification

import com.bkahlert.netmon.nmap.SingleElementUnwrappingJsonArraySerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray

/**
 * The uniform type identifiers owned and exported by the app.
 * @see <a href="https://developer.apple.com/documentation/bundleresources/information_property_list/utexportedtypedeclarations">UTExportedTypeDeclarations</a>
 */
val Bundle.exportedTypeDeclarations: List<UTExportedTypeDeclaration>?
    get() = info["UTExportedTypeDeclarations"]?.jsonArray?.map {
        PListFile.json.decodeFromJsonElement<UTExportedTypeDeclaration>(it)
    }

@Serializable
data class UTExportedTypeDeclaration(
    @SerialName("UTTypeIdentifier") val identifier: String,
    @Serializable(SingleElementUnwrappingJsonArraySerializer::class)
    @SerialName("UTTypeConformsTo") val conformsTo: List<String>? = null,
    @SerialName("UTTypeDescription") val description: String? = null,
    @SerialName("UTTypeIconFiles") val iconFiles: List<String>? = null,
    @SerialName("UTTypeIconFile") val iconFile: String? = null,
    @SerialName("UTTypeIcons") val icons: UTTypeIcons? = null,
    @SerialName("UTTypeIconName") val iconName: String? = null,
    @SerialName("UTTypeIsWildcard") val isWildcard: Boolean? = null,
    @SerialName("UTTypeTagSpecification") val tagSpecification: UTTypeTagSpecification? = null,
    @SerialName("UTTypeReferenceURL") val referenceURL: String? = null,
    @SerialName("UTKEXTIdentifier") val kextIdentifier: String? = null,
    @SerialName("public.mime-type") val publicMimeType: List<String>? = null,
) {
    override fun toString(): String = PListFile.json.encodeToString(this)
}

@Serializable
data class UTTypeIcons(
    @SerialName("_ISIconRecipe") val isIconRecipe: JsonElement? = null,
    @SerialName("ISGraphicIconConfiguration") val isGraphicIconConfiguration: JsonElement? = null,
    @SerialName("UTTypeIconBackgroundName") val iconBackgroundName: String? = null,
    @SerialName("UTTypeIconBackgroundText") val iconBackgroundText: String? = null,
    @SerialName("UTTypeIconBadgeName") val badgeName: String? = null,
    @SerialName("UTTypeIconName") val iconName: String? = null,
    @SerialName("UTTypeIconText") val iconText: String? = null,
    @SerialName("_UTTypeTemplateIconFile") val templateIconFile: String? = null,
    @SerialName("UTTypeIconFile") val iconFile: String? = null,
    @SerialName("UTTypeSymbolName") val symbolName: String? = null,
    @SerialName("UTTypeSymbolHeroName") val symbolHeroName: String? = null,
    @SerialName("UTTypeSymbolVariantNames") val symbolVariantNames: Map<String, String>? = null,
)

@Serializable
data class UTTypeTagSpecification(
    @Serializable(SingleElementUnwrappingJsonArraySerializer::class)
    @SerialName("com.apple.device-model-code")
    @JsonNames("com.example.device-model-code")
    val deviceModelCodes: List<String>? = null,

    @Serializable(SingleElementUnwrappingJsonArraySerializer::class)
    @SerialName("com.apple.ostype")
    @JsonNames("com.example.ostype")
    val osTypes: List<String>? = null,

    @Serializable(SingleElementUnwrappingJsonArraySerializer::class)
    @SerialName("public.mime-type")
    val mimeTypes: List<String>? = null,

    @Serializable(SingleElementUnwrappingJsonArraySerializer::class)
    @SerialName("public.filename-extension")
    val filenameExtensions: List<String>? = null,

    @Serializable(SingleElementUnwrappingJsonArraySerializer::class)
    @SerialName("com.apple.nspboard-type")
    val pasteBoardTypes: List<String>? = null,

    @Serializable(SingleElementUnwrappingJsonArraySerializer::class)
    @SerialName("public.bluetooth-vendor-product-id")
    val bluetoothVendorProductIds: List<String>? = null,

    @SerialName("UTTypeTagSpecification")
    val tagSpecification: UTTypeTagSpecification? = null,
) {
    override fun toString(): String = PListFile.json.encodeToString(this)
}
