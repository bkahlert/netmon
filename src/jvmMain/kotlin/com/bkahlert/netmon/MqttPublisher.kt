package com.bkahlert.netmon

import com.bkahlert.io.Logger
import com.bkahlert.kommons.exec.CommandLine
import com.bkahlert.serialization.JsonFormat
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import kotlin.io.path.pathString

data class MqttPublisher<T>(
    val topic: String,
    val host: String? = null,
    val port: Int? = null,
    val stringFormat: StringFormat = JsonFormat,
    val serializer: SerializationStrategy<T>,
    val identifier: String? = CommandLine("hostname").exec().readTextOrThrow().lowercase().substringBefore(".")
) : Publisher<T> {

    val binary = requireCommand(
        "mqtt", installationCommand = listOf(
            // macOS: brew install hivemq/mqtt-cli/mqtt-cli
            "wget https://github.com/hivemq/mqtt-cli/releases/download/v4.17.0/mqtt-cli-4.17.0.deb",
            "sudo apt install ./mqtt-cli-4.17.0.deb"
        ).joinToString(separator = " && ")
    ).pathString

    override fun publish(event: T): PublicationResult {
        val message = stringFormat.encodeToString(serializer, event)
        val bytes = message.encodeToByteArray()
        Logger.debug("Publishing message (${bytes.size} bytes) to $topic")

        return kotlin.runCatching {
            CommandLine(binary, buildList {
                add("pub")
                add("--topic")
                add(topic)
                add("--message")
                add(message)
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
            })
                .exec()
                .readLinesOrThrow()
                .filterNot { it.isBlank() }
                .forEach { Logger.debug(it) }
            MqttPublicationSuccess
        }.getOrElse {
            Logger.error("Error executing $binary: $it")
            MqttPublicationFailure(it)
        }
    }

    data object MqttPublicationSuccess : PublicationResult.Success
    data class MqttPublicationFailure(val cause: Throwable) : PublicationResult.Failure
}
