package com.bkahlert.netmon.identity

import com.bkahlert.netmon.Kind
import com.bkahlert.netmon.model_identification.DeviceModelCodes
import com.bkahlert.netmon.model_identification.load
import com.bkahlert.netmon.model_identification.resource
import com.bkahlert.netmon.serialization.JsonFormat
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlin.test.Test

class ModelCatalogBaselineTest {

    @Test
    fun every_shipped_model_matches_the_recorded_baseline() {
        val modelCodes = DeviceModelCodes.load(DeviceModelCodes.resource)
        val classifier = AppleCodes(modelCodes)
        val baselineResource = checkNotNull(javaClass.classLoader.getResource("assets/model-catalog-baseline.json"))
        val baseline = baselineResource.openStream().bufferedReader().use {
            JsonFormat.decodeFromString<Map<String, Kind?>>(it.readText())
        }
        val actual = modelCodes.sorted().associateWith { classifier.kindOf(it) }

        actual.keys shouldBe baseline.keys
        actual shouldBe baseline
    }
}
