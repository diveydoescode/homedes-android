package com.homedesign.android.domain.sketch

import com.homedesign.android.BuildConfig
import com.homedesign.android.data.remote.DrawingType
import com.homedesign.android.data.remote.JobStatusDto
import com.homedesign.android.data.remote.SketchApi
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

@Singleton
class SketchConvertClient @Inject constructor(
    private val api: SketchApi,
) {
    val serviceHost: String
        get() = runCatching {
            java.net.URI(BuildConfig.SKETCH_BASE_URL).host ?: BuildConfig.SKETCH_BASE_URL
        }.getOrDefault(BuildConfig.SKETCH_BASE_URL)

    suspend fun start(jpegBytes: ByteArray, drawingType: DrawingType): JobStatusDto {
        val imagePart = MultipartBody.Part.createFormData(
            "image",
            "sketch.jpg",
            jpegBytes.toRequestBody("image/jpeg".toMediaType()),
        )
        val typePart = drawingType.wire.toRequestBody("text/plain".toMediaType())
        val response = try {
            api.start(imagePart, typePart)
        } catch (e: Exception) {
            throw mapTransport(e)
        }
        if (response.code() == 202 || response.isSuccessful) {
            return response.body()
                ?: throw SketchApiException(SketchErrorCode.Invalid, "Empty job response")
        }
        throw httpError(response.code(), response.errorBody()?.string())
    }

    suspend fun poll(jobId: String): JobStatusDto {
        val response = try {
            api.poll(jobId)
        } catch (e: Exception) {
            throw mapTransport(e)
        }
        if (!response.isSuccessful) {
            throw httpError(response.code(), response.errorBody()?.string())
        }
        return response.body()
            ?: throw SketchApiException(SketchErrorCode.Invalid, "Empty poll response")
    }

    suspend fun download(jobId: String): ByteArray {
        val response = try {
            api.download(jobId)
        } catch (e: Exception) {
            throw mapTransport(e)
        }
        if (!response.isSuccessful) {
            throw httpError(response.code(), response.errorBody()?.string())
        }
        return response.body()?.bytes()
            ?: throw SketchApiException(SketchErrorCode.Invalid, "Empty download")
    }

    suspend fun cancel(jobId: String) {
        runCatching { api.cancel(jobId) }
    }

    private fun mapTransport(e: Exception): SketchApiException {
        val msg = e.message.orEmpty().lowercase()
        return if (
            e is java.net.SocketTimeoutException ||
            msg.contains("timeout") ||
            msg.contains("timed out")
        ) {
            SketchApiException(SketchErrorCode.Timeout, "request timed out")
        } else {
            SketchApiException(SketchErrorCode.Network, "network unreachable")
        }
    }

    private fun httpError(status: Int, body: String?): SketchApiException {
        val detail = body?.let { parseErrorDetail(it) }
        return SketchApiException(
            code = SketchErrorCode.Http,
            message = detail ?: "HTTP $status",
            status = status,
            detail = detail,
        )
    }

    private fun parseErrorDetail(body: String): String? = try {
        val json = JSONObject(body)
        json.optJSONObject("error_detail")?.optString("user_facing_message")
            ?.takeIf { it.isNotBlank() }
            ?: json.optString("error").takeIf { it.isNotBlank() }
            ?: json.optString("detail").takeIf { it.isNotBlank() }
            ?: json.optString("message").takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        body.takeIf { it.isNotBlank() && it.length < 280 }
    }
}
