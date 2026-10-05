package com.bkahlert.netmon.identity

import com.bkahlert.netmon.Kind

/** The HomeKit accessory categories (`_hap._tcp` TXT `ci`) that name a kind of device. */
object HapCategories {

    private val kinds: Map<Int, Kind> = mapOf(
        2 to Kind.HUB,
        5 to Kind.LAMP,
        6 to Kind.DOOR_LOCK,
        7 to Kind.SOCKET,
        8 to Kind.SOCKET,
        9 to Kind.THERMOSTAT,
        10 to Kind.SENSOR,
        14 to Kind.SHUTTER,
        15 to Kind.BUTTON,
        16 to Kind.ROUTER,
        17 to Kind.CAMERA,
        18 to Kind.DOOR_BELL,
        19 to Kind.AIR_PURIFIER,
        20 to Kind.THERMOSTAT,
        21 to Kind.AIR_CONDITIONER,
        24 to Kind.SET_TOP_BOX,
        25 to Kind.SPEAKER,
        26 to Kind.SPEAKER,
        27 to Kind.ROUTER,
        31 to Kind.TELEVISION,
        33 to Kind.ROUTER,
        34 to Kind.SPEAKER,
        35 to Kind.SET_TOP_BOX,
        36 to Kind.SET_TOP_BOX,
    )

    /** Returns the kind of the category, or `null` for categories without one or unparsable text. */
    fun kindOf(category: String?): Kind? = category?.trim()?.toIntOrNull()?.let(kinds::get)
}
