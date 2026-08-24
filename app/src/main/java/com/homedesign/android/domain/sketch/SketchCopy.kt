package com.homedesign.android.domain.sketch

/** Verbatim user-facing copy from web `src/sketch/copy.ts`. */
object SketchCopy {
    const val RETRY_CTA = "Try again"
    const val DIFFERENT_PHOTO_CTA = "Try a different photo"

    const val ERROR_401 = "The app's API key was rejected. It may have been rotated — update it and try again."
    const val ERROR_413 = "That photo is too large for the service. Try a smaller or less detailed image."
    const val ERROR_429 = "The service is at its rate or daily spend limit. Try again later."

    const val TIMEOUT_MESSAGE =
        "The conversion is taking longer than expected and the app stopped waiting. The plan may still finish on the server — try again in a few minutes."

    const val CANCELLED_MESSAGE = "Conversion cancelled."

    const val HEIC_UNREADABLE =
        "This image can't be opened. Export it as JPEG or PNG and try again."

    fun networkUnreachableMessage(host: String): String =
        "Couldn't reach the sketch service at $host. Check your connection and that the service is up."

    object Reel {
        const val READING_HEADLINE = "Reading your sketch"
        const val DRAWING_HEADLINE = "Drawing your plan"
        const val BUILDING_HEADLINE = "Building your model"
        const val STEADY_HEADLINE = "Still working on your plan"
        const val READING_PHASE = "reading the drawing…"
        const val WALLS_PHASE = "finding walls…"
        const val FURNITURE_PHASE = "placing furniture…"
        const val STEADY_PHASE = "still working on your plan…"
    }
}
