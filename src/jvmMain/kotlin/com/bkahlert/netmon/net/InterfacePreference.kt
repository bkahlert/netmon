package com.bkahlert.netmon.net

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

/**
 * Whether the network interface with the given [name] is wireless:
 * Linux exposes `/sys/class/net/<name>/wireless` for one, and elsewhere the name says it (`wlan0`, `wlp2s0`).
 */
fun isWireless(name: String, sysfs: Path = Paths.get("/sys/class/net")): Boolean =
    sysfs.resolve(name).resolve("wireless").exists() || name.startsWith("wl")

/**
 * Keeps one element per network, as identified by [network]: when several interfaces sit on the same network,
 * the first [wired] one wins, otherwise the first one. A board on Wi-Fi and on a cable in the same LAN then scans
 * it once, over the cable.
 */
fun <T> Iterable<T>.onePerNetwork(network: (T) -> Any, wired: (T) -> Boolean): List<T> =
    groupBy(network).values.map { candidates -> candidates.firstOrNull(wired) ?: candidates.first() }
