package com.bkahlert.netmon.net

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.test.Test

class InterfacePreferenceTest {

    private val wired: (Pair<String, String>) -> Boolean = { (name, _) -> !name.startsWith("wl") }

    @Test
    fun keeps_every_distinct_network() {
        listOf("eth0" to "10.0.0.0/24", "wlan0" to "10.1.0.0/24")
            .onePerNetwork({ it.second }, wired) shouldContainExactly listOf("eth0" to "10.0.0.0/24", "wlan0" to "10.1.0.0/24")
    }

    @Test
    fun prefers_the_wired_interface_on_a_shared_network() {
        listOf("wlan0" to "192.168.16.0/23", "eth0" to "192.168.16.0/23", "usb0" to "10.10.10.40/29")
            .onePerNetwork({ it.second }, wired) shouldContainExactly listOf("eth0" to "192.168.16.0/23", "usb0" to "10.10.10.40/29")
    }

    @Test
    fun keeps_the_first_interface_when_none_is_wired() {
        listOf("wlan0" to "192.168.16.0/23", "wlan1" to "192.168.16.0/23")
            .onePerNetwork({ it.second }, wired) shouldContainExactly listOf("wlan0" to "192.168.16.0/23")
    }

    @Test
    fun wireless_by_sysfs_entry_or_name() {
        val sysfs = Files.createTempDirectory("sys-class-net")
        sysfs.resolve("foo0/wireless").createDirectories()
        sysfs.resolve("eth0").createDirectories()

        isWireless("foo0", sysfs) shouldBe true
        isWireless("eth0", sysfs) shouldBe false
        isWireless("wlan0", sysfs) shouldBe true
    }
}
