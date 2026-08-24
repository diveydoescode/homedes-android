package com.homedesign.android.domain.sketch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SketchDomainTest {
    @Test
    fun nextPollDelay_switchesAfterStableWindow() {
        assertEquals(2_000L, nextPollDelayMs(0))
        assertEquals(2_000L, nextPollDelayMs(29_999))
        assertEquals(5_000L, nextPollDelayMs(30_000))
    }

    @Test
    fun pendingJob_roundTripAndStale() {
        val now = 1_000_000L
        val encoded = encodePendingSketchJob(PendingSketchJob("job-1", now))
        val parsed = parsePendingSketchJob(encoded, now)
        assertEquals("job-1", parsed?.jobId)

        val stale = parsePendingSketchJob(encoded, now + SketchConstants.PENDING_JOB_MAX_AGE_MS + 1)
        assertNull(stale)

        val legacy = parsePendingSketchJob("bare-id", now)
        assertEquals("bare-id", legacy?.jobId)
    }

    @Test
    fun flowError_maps413AndTimeout() {
        val http = flowErrorFromApi(
            SketchApiException(SketchErrorCode.Http, "too large", status = 413),
            "example.com",
        )
        assertEquals(SketchCopy.ERROR_413, http.message)
        assertTrue(http.isRetryable)

        val timeout = flowErrorFromApi(
            SketchApiException(SketchErrorCode.Timeout, "timeout"),
            "example.com",
        )
        assertEquals(SketchCopy.TIMEOUT_MESSAGE, timeout.message)
    }
}
