package com.homedesign.android.domain.geom

/** Trace underlay scale. Width is plan-cm; height follows image aspect. */
const val TRACE_MIN_CM = 200.0
const val TRACE_MAX_CM = 5000.0
const val TRACE_DEFAULT_CM = 1000.0

fun clampTraceWidthCM(cm: Double): Double {
    if (!cm.isFinite()) return TRACE_DEFAULT_CM
    return cm.coerceIn(TRACE_MIN_CM, TRACE_MAX_CM)
}

fun traceHeightCM(widthCM: Double, imageWidth: Int, imageHeight: Int): Double {
    val w = clampTraceWidthCM(widthCM)
    if (imageWidth <= 0 || !imageHeight.toDouble().isFinite()) return w
    return w * (imageHeight.toDouble() / imageWidth.toDouble())
}

fun formatTraceWidthLabel(widthCM: Double): String =
    "${kotlin.math.round(clampTraceWidthCM(widthCM) / 100.0).toInt()} m wide"
