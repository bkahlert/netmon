package mqtt

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class MQTTTest {

    @Test
    fun resolves_to_the_client_api_and_not_to_an_empty_module() {
        jsTypeOf(MQTT.asDynamic().connect) shouldBe "function"
    }
}
