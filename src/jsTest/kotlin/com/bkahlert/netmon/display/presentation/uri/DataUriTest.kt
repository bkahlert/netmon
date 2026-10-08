package com.bkahlert.netmon.display.presentation.uri

import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class DataUriTest {

    @Test
    fun svg() {
        val uri = DataUri.svg("<svg/>")
        uri should {
            it.toString() shouldBe "data:image/svg+xml;base64,PHN2Zy8+"
            it.isSvg shouldBe true
        }
    }

    @Test
    fun parse_percent_encoded_text() {
        val uri = DataUri.parse("data:,Hello%2C%20World")
        uri should {
            it.mediaType shouldBe null
            it.data.decodeToString() shouldBe "Hello, World"
            it.isSvg shouldBe false
        }
    }

    @Test
    fun parse_with_media_type_parameters() {
        val uri = DataUri.parse("data:image/svg+xml;charset=utf-8;base64,PHN2Zy8+")
        uri should {
            it.mediaType shouldBe "image/svg+xml;charset=utf-8"
            it.isSvg shouldBe true
        }
    }

    @Test
    fun string_form_round_trips() {
        val uri = DataUri.parse(DataUri.svg("<svg><path d=\"M0 0\"/></svg>").toString())
        uri.data.decodeToString() shouldBe "<svg><path d=\"M0 0\"/></svg>"
    }
}
