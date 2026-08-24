package com.homedesign.android.domain.sketch

/** Port of web `src/sketch/constants.ts`. */
object SketchConstants {
    const val MAX_UPLOAD_BYTES = 10 * 1024 * 1024
    const val TARGET_LONG_EDGE = 2048
    const val JPEG_QUALITY = 85
    const val PENDING_JOB_MAX_AGE_MS = 60L * 60L * 1000L
    const val POLL_INTERVAL_MS = 2_000L
    const val POLL_INTERVAL_AFTER_STABLE_MS = 5_000L
    const val POLL_STABLE_AFTER_MS = 30_000L
    const val POLL_OVERALL_TIMEOUT_MS = 900_000L
    const val MAX_CONSECUTIVE_POLL_FAILS = 8
    const val CROP_MIN_SIDE_PX = 60f
    const val CROP_HANDLE_PX = 28f
    const val CROP_HIT_SLOP_PX = 22f
}
