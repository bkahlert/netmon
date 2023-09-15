package com.bkahlert.netmon.model_identification

/** A patch that updates [UTExportedTypeDeclaration] instances so that the computed [Icon] is a [Icon.Symbol]. */
val CoreTypesSymbolPatch: (UTExportedTypeDeclaration) -> UTExportedTypeDeclaration = { type ->
    when (type.takeUnless { it.icons?.symbolName in SFSymbols5.keys }?.identifier) {
        "com.apple.mac",
        -> type.copy(icons = UTTypeIcons(symbolName = "display"))

        "com.apple.ipad",
        -> type.copy(icons = UTTypeIcons(symbolName = "ipad"))

        "com.apple.ipad-air2-A1566-e1ccb5",
        "com.apple.ipad-air2-A1567-e1ccb5",
        "com.apple.ipad-gen5-A1822-3",
        "com.apple.ipad-gen5-A1823-3",
        "com.apple.ipad-air3-wifi-3",
        "com.apple.ipad-air3-wwan-3",
        "com.apple.ipad-6-A1893-3",
        "com.apple.ipad-6-A1954-3",
        "com.apple.ipad-mini3-A1599-e1ccb5",
        "com.apple.ipad-mini3-A1600-e1ccb5",
        "com.apple.ipad-mini3-A1601-e1ccb5",
        "com.apple.ipad-mini4-A1538-e1ccb5",
        "com.apple.ipad-mini4-A1550-e1ccb5",
        "com.apple.ipad-mini5-wifi-3",
        "com.apple.ipad-mini5-wwan-3",
        -> type.copy(icons = UTTypeIcons(symbolName = "ipad.gen1"))

        "com.apple.ipad-air4-5",
        "com.apple.ipad-air5-7",
        "com.apple.ipad-pro-A1584-e1ccb5",
        "com.apple.ipad-pro-A1652-e1ccb5",
        "com.apple.ipad-pro-A1670-3",
        "com.apple.ipad-pro-A1821-3",
        "com.apple.ipad-7-wifi-3",
        "com.apple.ipad-7-wwan-3",
        "com.apple.ipad-8-wifi-3",
        "com.apple.ipad-8-wwan-3",
        -> type.copy(icons = UTTypeIcons(symbolName = "ipad.gen2"))

        "com.apple.emac",
        -> type.copy(icons = UTTypeIcons(symbolName = "desktopcomputer"))

        "com.apple.powermac",
        "com.apple.mac.tower",
        -> type.copy(icons = UTTypeIcons(symbolName = "macpro.gen1"))

        "com.apple.xserve",
        -> type.copy(icons = UTTypeIcons(symbolName = "xserve"))

        "com.apple.mac.rackmount",
        -> type.copy(icons = UTTypeIcons(symbolName = "xserve.raid"))

        else -> type
    }
}
