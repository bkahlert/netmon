package com.bkahlert.netmon.ui

import com.bkahlert.netmon.fritz2.runTest
import dev.fritz2.core.render
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

class IconKtTest {

    @Test
    fun svg_data_uri_is_inlined() = runTest {
        val container = document.createElement("div") as HTMLElement
        document.body?.appendChild(container)

        render(container) { icon(SFSymbols.display) }
        delay(50.milliseconds)

        withClue(container.outerHTML) {
            container.querySelector("svg").shouldNotBeNull() should {
                it.getAttribute("data-symbol-name") shouldBe "display"
                it.getAttribute("viewBox") shouldBe "0 0 29 29"
                it.querySelectorAll("path").length shouldBe 2
            }
        }
    }
}
