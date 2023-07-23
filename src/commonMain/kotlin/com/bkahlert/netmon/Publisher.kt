package com.bkahlert.netmon

import com.bkahlert.exec.ShellScript
import com.bkahlert.exec.checkCommand
import com.bkahlert.io.Logger
import com.bkahlert.serialization.JsonFormat
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

fun interface Publisher<T> {
    fun publish(event: T): PublicationResult
}

sealed interface PublicationResult {
    interface Success : PublicationResult
    interface Failure : PublicationResult
}


data class MqttPublisher<T>(
    val topic: String,
    val host: String? = null,
    val port: Int? = null,
    val stringFormat: StringFormat = JsonFormat,
    val serializer: SerializationStrategy<T>,
    val identifier: String? = ShellScript("hostname").execute().singleOrNull()?.lowercase()?.substringBefore(".")
) : Publisher<T> {

    init {
        checkCommand(
            "mqtt", installationCommand = listOf(
                // macOS: brew install hivemq/mqtt-cli/mqtt-cli
                "wget https://github.com/hivemq/mqtt-cli/releases/download/v4.17.0/mqtt-cli-4.17.0.deb",
                "sudo apt install ./mqtt-cli-4.17.0.deb"
            ).joinToString(separator = " && ")
        )
    }

    override fun publish(event: T): PublicationResult {
        val message = stringFormat.encodeToString(serializer, event)
        val bytes = message.encodeToByteArray()
        Logger.debug("Publishing message (${bytes.size} bytes) to $topic")

        val cmdline = buildList {
            add("mqtt")
            add("pub")
            add("--topic")
            add(topic)
            add("--message")
            add(""""$(echo '${Base64.encode(bytes)}' | base64 -d)"""")
            host?.also {
                add("-h")
                add(it)
            }
            port?.also {
                add("-p")
                add(it.toString())
            }
            add("--payloadFormatIndicator")
            add("UTF_8")
            if (stringFormat is Json) {
                add("--contentType")
                add("application/json")
            }

            if (identifier != null) {
                add("--identifier")
                add(identifier)
            }
        }

        val exitCode = ShellScript(cmdline.joinToString(" ") + "; printf '\\n%s' $?")
            .execute()
            .filterNot { it.isBlank() }
            .windowed(2, partialWindows = true)
            .dropWhile {
                val lastLine = it.size == 1
                if (!lastLine) Logger.debug(it.first())
                !lastLine
            }
            .last().single().toInt()

        return if (exitCode == 0) {
            MqttPublicationSuccess
        } else {
            Logger.error("Publishing message failed with exit code $exitCode")
            MqttPublicationFailure(exitCode)
        }
    }
}

data object MqttPublicationSuccess : PublicationResult.Success
data class MqttPublicationFailure(val exitCode: Int) : PublicationResult.Failure
