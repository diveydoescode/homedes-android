package com.homedesign.android.domain.model

import kotlinx.serialization.json.Json

/** Shared JSON config for Home round-trip (`kind` discriminator for WallSpan). */
val DefaultHomeJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
    classDiscriminator = "kind"
    isLenient = true
}
