package com.player.tv.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// Xtream servers are inconsistent: the same field can be a number, a string, "", null or false
// depending on the panel software. These helpers read fields leniently instead of failing.

internal val json = Json { ignoreUnknownKeys = true; isLenient = true }

internal fun parseJson(text: String): JsonElement = json.parseToJsonElement(text)

internal fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

internal fun JsonObject.int(key: String): Int? = str(key)?.let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() }

internal fun JsonObject.long(key: String): Long? = str(key)?.let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() }

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

internal fun JsonElement.objects(): List<JsonObject> = (this as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()

/** First non-empty string of a field that may be a string or an array of strings (e.g. backdrop_path). */
internal fun JsonObject.firstString(key: String): String? = when (val v = this[key]) {
    is JsonArray -> v.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
    is JsonPrimitive -> v.contentOrNull?.takeIf(String::isNotBlank)
    else -> null
}
