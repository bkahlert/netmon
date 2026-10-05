package mqtt

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import kotlin.test.Test

class ExtensionsKtTest {

    @Test
    fun messages_decode_a_payload_mqtt_js_delivers_as_unsigned_bytes_as_utf_8() = runTest {
        val client = clientDelivering(Uint8Array("Björn".encodeToByteArray().unsafeCast<Int8Array>().buffer))

        val message = client.messages.first()

        message.second.decodeToString() shouldBe "Björn"
    }
}

private fun clientDelivering(payload: Uint8Array): MqttClient {
    val client: dynamic = js("{}")
    client.on = { event: String, listener: dynamic -> if (event == "message") listener("topic", payload, null) }
    client.off = { _: String, _: dynamic -> }
    return client.unsafeCast<MqttClient>()
}
