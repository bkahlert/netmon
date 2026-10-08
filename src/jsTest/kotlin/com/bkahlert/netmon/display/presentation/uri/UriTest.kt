package com.bkahlert.netmon.display.presentation.uri

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class UriTest {

    @Test
    fun query_parameters() {
        val uri = "http://netmon.local/?broker.host=broker.example.com&broker.port=1883#top".toUri()
        uri.queryParameters should {
            it.get("broker.host") shouldBe "broker.example.com"
            it.get("broker.port") shouldBe "1883"
            it.get("missing").shouldBeNull()
        }
    }

    @Test
    fun plain_uri_keeps_its_string_form() {
        val uri = "images/loading.svg".toUri()
        uri.toString() shouldBe "images/loading.svg"
    }

    @Test
    fun data_uri_is_parsed() {
        val uri = "data:image/svg+xml;base64,PHN2Zy8+".toUri()
        uri.shouldBeInstanceOf<DataUri>() should {
            it.mediaType shouldBe "image/svg+xml"
            it.data.decodeToString() shouldBe "<svg/>"
            it.isSvg shouldBe true
        }
    }

    @Test
    fun invalid_data_uri_is_null() {
        "data:nonsense".toUriOrNull().shouldBeNull()
    }
}
