package com.homedesign.android.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/**
 * Retrofit mirror of web `sketchClient.ts` against `/api/sketch`.
 *
 * POST multipart → 202 JobStatus
 * GET /{id} → 200 JobStatus
 * GET /{id}/file → bytes
 * POST /{id}/cancel → fire-and-forget
 */
interface SketchApi {
    @Multipart
    @POST("api/sketch")
    suspend fun start(
        @Part image: MultipartBody.Part,
        @Part("drawing_type") drawingType: RequestBody,
    ): Response<JobStatusDto>

    @GET("api/sketch/{id}")
    suspend fun poll(@Path("id") id: String): Response<JobStatusDto>

    @GET("api/sketch/{id}/file")
    suspend fun download(@Path("id") id: String): Response<ResponseBody>

    @POST("api/sketch/{id}/cancel")
    suspend fun cancel(@Path("id") id: String): Response<Unit>
}
