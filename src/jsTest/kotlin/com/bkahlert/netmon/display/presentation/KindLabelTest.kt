package com.bkahlert.netmon.display.presentation

import com.bkahlert.netmon.contract.Kind
import com.bkahlert.netmon.display.presentation.label
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class KindLabelTest {

    @Test
    fun label_splits_the_token_into_words() {
        Kind.SET_TOP_BOX.label shouldBe "Set Top Box"
        Kind.TELEVISION.label shouldBe "Television"
        Kind.IP_PHONE.label shouldBe "IP Phone"
    }
}
