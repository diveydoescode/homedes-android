package com.homedesign.android.domain.sketch

import com.homedesign.android.data.remote.JobStatusDto

enum class SketchErrorCode {
    Network,
    Timeout,
    Http,
    Failed,
    Cancelled,
    Invalid,
}

class SketchApiException(
    val code: SketchErrorCode,
    message: String,
    val status: Int? = null,
    val detail: String? = null,
) : Exception(message)

data class SketchFlowError(
    val message: String,
    val retryCta: String = SketchCopy.RETRY_CTA,
    val isRetryable: Boolean = true,
)

fun userFacingJobMessage(status: JobStatusDto, fallback: String): String =
    status.errorDetail?.userFacingMessage?.takeIf { it.isNotBlank() }
        ?: status.error?.takeIf { it.isNotBlank() }
        ?: fallback

fun retryCtaForMessage(message: String, backendFailed: Boolean): String {
    if (!backendFailed) return SketchCopy.RETRY_CTA
    val lower = message.lowercase()
    return if (
        lower.contains("dimension") ||
        lower.contains("ocr") ||
        lower.contains("unreadable")
    ) {
        SketchCopy.DIFFERENT_PHOTO_CTA
    } else {
        SketchCopy.RETRY_CTA
    }
}

fun flowErrorFromApi(error: Throwable, serviceHost: String): SketchFlowError {
    if (error is SketchApiException) {
        return when (error.code) {
            SketchErrorCode.Cancelled -> SketchFlowError(
                message = SketchCopy.CANCELLED_MESSAGE,
                retryCta = SketchCopy.RETRY_CTA,
            )
            SketchErrorCode.Network -> SketchFlowError(
                message = SketchCopy.networkUnreachableMessage(serviceHost),
            )
            SketchErrorCode.Timeout -> SketchFlowError(
                message = SketchCopy.TIMEOUT_MESSAGE,
            )
            SketchErrorCode.Http -> {
                val status = error.status ?: 0
                val text = when (status) {
                    401 -> SketchCopy.ERROR_401
                    413 -> SketchCopy.ERROR_413
                    429 -> error.detail?.takeIf { it.isNotBlank() } ?: SketchCopy.ERROR_429
                    else -> error.detail?.takeIf { it.isNotBlank() }
                        ?: "The service returned an error ($status)."
                }
                SketchFlowError(message = text, retryCta = retryCtaForMessage(text, false))
            }
            SketchErrorCode.Failed -> SketchFlowError(
                message = error.message ?: "Conversion failed.",
                retryCta = retryCtaForMessage(error.message.orEmpty(), true),
            )
            SketchErrorCode.Invalid -> SketchFlowError(
                message = error.message ?: "Something went wrong.",
            )
        }
    }
    return SketchFlowError(message = error.message ?: "Something went wrong.")
}
