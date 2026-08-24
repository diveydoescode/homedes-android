package com.homedesign.android.domain.sketch

data class PendingSketchJob(
    val jobId: String,
    val startedAt: Long,
)

fun encodePendingSketchJob(job: PendingSketchJob): String =
    """{"jobId":${jsonString(job.jobId)},"startedAt":${job.startedAt}}"""

fun parsePendingSketchJob(raw: String?, now: Long = System.currentTimeMillis()): PendingSketchJob? {
    if (raw.isNullOrBlank()) return null
    val record = parsePendingRecord(raw.trim(), now) ?: return null
    if (now - record.startedAt > SketchConstants.PENDING_JOB_MAX_AGE_MS) return null
    return record
}

private fun parsePendingRecord(raw: String, now: Long): PendingSketchJob? {
    if (!raw.startsWith("{")) {
        return PendingSketchJob(jobId = raw, startedAt = now)
    }
    val jobId = extractJsonString(raw, "jobId")
        ?: extractJsonString(raw, "job_id")
        ?: return null
    val startedAt = extractJsonLong(raw, "startedAt") ?: now
    return PendingSketchJob(jobId = jobId, startedAt = startedAt)
}

private fun jsonString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

private fun extractJsonString(json: String, key: String): String? {
    val keyToken = "\"$key\""
    val keyIdx = json.indexOf(keyToken)
    if (keyIdx < 0) return null
    val colon = json.indexOf(':', keyIdx + keyToken.length)
    if (colon < 0) return null
    var i = colon + 1
    while (i < json.length && json[i].isWhitespace()) i++
    if (i >= json.length || json[i] != '"') return null
    i++
    val sb = StringBuilder()
    while (i < json.length) {
        val c = json[i]
        when {
            c == '\\' && i + 1 < json.length -> {
                sb.append(json[i + 1])
                i += 2
            }
            c == '"' -> return sb.toString()
            else -> {
                sb.append(c)
                i++
            }
        }
    }
    return null
}

private fun extractJsonLong(json: String, key: String): Long? {
    val keyToken = "\"$key\""
    val keyIdx = json.indexOf(keyToken)
    if (keyIdx < 0) return null
    val colon = json.indexOf(':', keyIdx + keyToken.length)
    if (colon < 0) return null
    var i = colon + 1
    while (i < json.length && json[i].isWhitespace()) i++
    val start = i
    while (i < json.length && (json[i].isDigit() || json[i] == '-')) i++
    if (start == i) return null
    return json.substring(start, i).toLongOrNull()
}
