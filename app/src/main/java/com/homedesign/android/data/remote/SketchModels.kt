package com.homedesign.android.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class DrawingType(val wire: String) {
    SKETCH("sketch"),
    CAD("cad"),
}

@Serializable
data class ErrorDetailDto(
    val code: String? = null,
    val message: String? = null,
    @SerialName("user_facing_message") val userFacingMessage: String? = null,
)

@Serializable
data class JobWarningDto(
    val code: String? = null,
    val severity: String? = null,
    val title: String? = null,
    @SerialName("user_facing_message") val userFacingMessage: String? = null,
)

@Serializable
data class JobStatusDto(
    @SerialName("job_id") val jobId: String,
    val status: String,
    val error: String? = null,
    @SerialName("error_detail") val errorDetail: ErrorDetailDto? = null,
    val progress: Double? = null,
    val warnings: List<JobWarningDto>? = null,
    @SerialName("result_url") val resultUrl: String? = null,
    @SerialName("cost_usd") val costUsd: Double? = null,
    @SerialName("plan_name") val planName: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

fun JobStatusDto.isTerminal(): Boolean =
    status == "completed" || status == "failed" || status == "cancelled"
