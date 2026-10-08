package com.bkahlert.netmon.support.config

import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class EnvironmentKeysKtTest {

    @Test
    fun environment_key() = runTest {
        forAll(
            row(listOf("verbosity"), "VERBOSITY"),
            row(listOf("broker", "host"), "BROKER_HOST"),
            row(listOf("nmap", "dataDir"), "NMAP_DATA_DIR"),
            row(listOf("network", "minHostBits"), "NETWORK_MIN_HOST_BITS"),
            row(listOf("scan", "outdatedThreshold"), "SCAN_OUTDATED_THRESHOLD"),
        ) { path, expected ->
            environmentKey(path) shouldBe expected
        }
    }
}
