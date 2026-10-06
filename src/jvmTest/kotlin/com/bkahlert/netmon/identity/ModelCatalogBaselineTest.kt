package com.bkahlert.netmon.identity

import com.bkahlert.netmon.Kind
import com.bkahlert.netmon.serialization.JsonFormat
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlin.test.Test

class ModelCatalogBaselineTest {

    @Test
    fun every_shipped_model_matches_the_recorded_baseline() {
        val modelCatalog = loadModelCatalog()
        val classifier = AppleCodes(modelCatalog)
        val baselineResource = checkNotNull(javaClass.classLoader.getResource("assets/model-catalog-baseline.json"))
        val baseline = baselineResource.openStream().bufferedReader().use {
            JsonFormat.decodeFromString<Map<String, Kind?>>(it.readText())
        }
        val actual = modelCatalog.sorted().associateWith { classifier.kindOf(it) }

        actual.keys shouldBe baseline.keys
        actual shouldBe baseline
    }
}
