package com.homedesign.android.domain.io

import com.homedesign.android.domain.model.DefaultHomeJson
import com.homedesign.android.domain.model.Home
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

val HomeJson = DefaultHomeJson

private val RUNTIME_HOME_KEYS = setOf(
    "topologyVersion",
    "furnitureRevision",
    "styleVersion",
    "extractedAssetURLs",
)

/** Drop null-valued object properties (Swift decodeIfPresent parity). */
fun stripNullProps(element: kotlinx.serialization.json.JsonElement): kotlinx.serialization.json.JsonElement {
    return when (element) {
        is kotlinx.serialization.json.JsonArray ->
            kotlinx.serialization.json.JsonArray(element.map { stripNullProps(it) })
        is JsonObject -> {
            JsonObject(
                element.mapNotNull { (k, v) ->
                    if (v is kotlinx.serialization.json.JsonNull) null
                    else k to stripNullProps(v)
                }.toMap(),
            )
        }
        else -> element
    }
}

fun omitRuntimeFields(element: kotlinx.serialization.json.JsonElement): kotlinx.serialization.json.JsonElement {
    if (element !is JsonObject) return element
    return JsonObject(element.filterKeys { it !in RUNTIME_HOME_KEYS })
}

fun decodeHome(data: String): Home {
    val parsed = HomeJson.parseToJsonElement(data)
    val cleaned = stripNullProps(omitRuntimeFields(parsed))
    val persist = HomeJson.decodeFromJsonElement(Home.serializer(), cleaned)
    return persist.copy(
        topologyVersion = 0,
        furnitureRevision = 0,
        styleVersion = 0,
        extractedAssetURLs = emptyMap(),
    )
}

fun decodeHome(data: ByteArray): Home =
    decodeHome(data.toString(Charsets.UTF_8))

fun encodeHome(home: Home): String {
    val element = HomeJson.encodeToJsonElement(Home.serializer(), home)
    val cleaned = omitRuntimeFields(element)
    return HomeJson.encodeToString(cleaned)
}

fun encodeHomeObject(home: Home): JsonObject {
    val element = HomeJson.encodeToJsonElement(Home.serializer(), home)
    return omitRuntimeFields(element).jsonObject
}
