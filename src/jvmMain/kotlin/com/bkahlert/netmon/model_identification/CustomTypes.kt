package com.bkahlert.netmon.model_identification

object CustomTypes : Types(
    UTExportedTypeDeclaration(
        identifier = "com.bkahlert.netmon.media-stick",
        description = "Media Stick",
        icons = UTTypeIcons(symbolName = "mediastick"),
        tagSpecification = UTTypeTagSpecification(deviceModelCodes = listOf("MediaStick")),
    ),
    UTExportedTypeDeclaration(
        identifier = "com.bkahlert.netmon.speaker",
        description = "Speaker",
        icons = UTTypeIcons(symbolName = "hifispeaker"),
        tagSpecification = UTTypeTagSpecification(deviceModelCodes = listOf("Speaker")),
    ),
    UTExportedTypeDeclaration(
        identifier = "com.bkahlert.netmon.wireless-speaker",
        description = "Wireless Speaker",
        tagSpecification = UTTypeTagSpecification(deviceModelCodes = listOf("WirelessSpeaker")),
        conformsTo = listOf("com.bkahlert.netmon.speaker"),
    ),
)

object AmazonTypes : Types(
    UTExportedTypeDeclaration(
        identifier = "com.amazon.firetv",
        description = "Fire TV",
        tagSpecification = UTTypeTagSpecification(deviceModelCodes = listOf("FireTV", "Fire TV")),
        conformsTo = listOf("com.bkahlert.netmon.media-stick"),
    ),
    UTExportedTypeDeclaration(
        identifier = "com.amazon.firetv-stick",
        description = "Fire TV Stick",
        tagSpecification = UTTypeTagSpecification(deviceModelCodes = listOf("FireTVStick")),
        conformsTo = listOf("com.amazon.firetv"),
    ),
    UTExportedTypeDeclaration(
        identifier = "com.amazon.firetv-stick-4k",
        description = "Fire TV Stick 4K",
        tagSpecification = UTTypeTagSpecification(deviceModelCodes = listOf("FireTVStick4K")),
        conformsTo = listOf("com.amazon.firetv-stick"),
    ),
)

object SonosTypes : Types(
    UTExportedTypeDeclaration(
        identifier = "com.sonos.one-sl",
        description = "One SL",
        tagSpecification = UTTypeTagSpecification(deviceModelCodes = listOf("OneSL", "One SL")),
        conformsTo = listOf("com.bkahlert.netmon.wireless-speaker"),
    ),
)
