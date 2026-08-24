package com.homedesign.android.domain.sketch

import com.homedesign.android.data.remote.JobStatusDto
import com.homedesign.android.data.remote.isTerminal
import kotlinx.coroutines.delay

fun nextPollDelayMs(elapsedMs: Long): Long =
    if (elapsedMs < SketchConstants.POLL_STABLE_AFTER_MS) {
        SketchConstants.POLL_INTERVAL_MS
    } else {
        SketchConstants.POLL_INTERVAL_AFTER_STABLE_MS
    }

data class PollLoopDeps(
    val poll: suspend (String) -> JobStatusDto,
    val cancel: suspend (String) -> Unit,
    val now: () -> Long = { System.currentTimeMillis() },
    val onStatus: (JobStatusDto) -> Unit = {},
    val isCancelled: () -> Boolean = { false },
    val overallTimeoutMs: Long = SketchConstants.POLL_OVERALL_TIMEOUT_MS,
    val maxConsecutiveFails: Int = SketchConstants.MAX_CONSECUTIVE_POLL_FAILS,
)

suspend fun pollUntilTerminal(jobId: String, deps: PollLoopDeps): JobStatusDto {
    val started = deps.now()
    var consecutiveFailures = 0

    while (!deps.isCancelled()) {
        val elapsed = deps.now() - started
        if (elapsed > deps.overallTimeoutMs) {
            try {
                val last = deps.poll(jobId)
                if (last.status == "completed") {
                    deps.onStatus(last)
                    return last
                }
            } catch (_: Exception) {
                /* last chance failed — still time out */
            }
            runCatching { deps.cancel(jobId) }
            throw SketchApiException(SketchErrorCode.Timeout, "poll overall timeout")
        }

        val status = try {
            deps.poll(jobId).also { consecutiveFailures = 0 }
        } catch (err: Exception) {
            if (deps.isCancelled()) {
                throw SketchApiException(SketchErrorCode.Cancelled, "cancelled")
            }
            consecutiveFailures += 1
            if (consecutiveFailures >= deps.maxConsecutiveFails) {
                throw err
            }
            delay(SketchConstants.POLL_INTERVAL_AFTER_STABLE_MS)
            continue
        }

        deps.onStatus(status)
        when (status.status) {
            "completed" -> return status
            "failed" -> throw SketchApiException(
                SketchErrorCode.Failed,
                userFacingJobMessage(status, "Backend reported a failed job with no message."),
            )
            "cancelled" -> throw SketchApiException(SketchErrorCode.Cancelled, "cancelled")
            else -> {
                if (status.isTerminal()) return status
                delay(nextPollDelayMs(deps.now() - started))
            }
        }
    }
    throw SketchApiException(SketchErrorCode.Cancelled, "cancelled")
}
