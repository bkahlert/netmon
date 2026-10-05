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

    @Test
    fun messages_decode_only_the_slice_of_a_pooled_buffer_a_payload_occupies() = runTest {
        val payload = "Björn".encodeToByteArray()
        val client = clientDelivering(slice(payload, padding = 3))

        val message = client.messages.first()

        message.second.decodeToString() shouldBe "Björn"
    }
}

private fun slice(payload: ByteArray, padding: Int): Uint8Array {
    val pool = Uint8Array(padding + payload.size + padding)
    pool.set(Uint8Array(payload.unsafeCast<Int8Array>().buffer), padding)
    return Uint8Array(pool.buffer, padding, payload.size)
}

private fun clientDelivering(payload: Uint8Array): MqttClient {
    val client: dynamic = js("{}")
    client.on = { event: String, listener: dynamic -> if (event == "message") listener("topic", payload, null) }
    client.off = { _: String, _: dynamic -> }
    return client.unsafeCast<MqttClient>()
}
