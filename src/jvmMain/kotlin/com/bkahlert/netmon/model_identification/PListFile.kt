package com.bkahlert.netmon.model_identification

import com.bkahlert.netmon.exec.CommandLine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.io.path.writeText

@JvmInline
value class PListFile(val path: Path) {

    fun read(): JsonObject = CommandLine("plutil", "-convert", "json", "-r", "-o", "-", path.pathString)
        .exec()
        .readTextOrThrow()
        .let { Json.decodeFromString(it) }

    fun write(json: JsonObject): String {
        path.writeText(Json.encodeToString(json))
        return CommandLine("plutil", "-convert", "xml1", path.pathString)
            .exec()
            .readTextOrThrow()
    }

    companion object {
        val json = Json {
            prettyPrint = true
        }
    }
}
