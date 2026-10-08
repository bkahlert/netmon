package com.bkahlert.netmon.display.support.console

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.browser.document

class OnScreenConsoleTest {

    @Test
    fun dispose_removes_all_containers_and_releases_console_observation() {
        val parent = document.createElement("div")
        val console = recordingConsole()
        val onScreenConsole = OnScreenConsole(console)

        onScreenConsole.enable(parent)
        console.asDynamic().log("first")
        parent.childElementCount shouldBe 1

        val fading = checkNotNull(parent.firstElementChild)
        onScreenConsole.disable()
        fading.classList.contains("h-0") shouldBe true
        fading.classList.contains("opacity-0") shouldBe true
        onScreenConsole.enable(parent)
        console.asDynamic().log("second")
        parent.childElementCount shouldBe 2

        onScreenConsole.disable()
        onScreenConsole.dispose()
        onScreenConsole.dispose()
        console.asDynamic().log("after disposal")

        parent.childElementCount shouldBe 0
    }
}
