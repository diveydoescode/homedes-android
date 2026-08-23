package com.homedesign.android.domain.settings

import kotlinx.coroutines.flow.Flow

data class UserSettings(
    val hasOnboarded: Boolean = false,
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val useMetric: Boolean = true,
    val darkTheme: Boolean? = null,
)

interface SettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun getSettings(): UserSettings
    suspend fun setIdentity(firstName: String, lastName: String)
    suspend fun setUseMetric(useMetric: Boolean)
    suspend fun setEmail(email: String)
    suspend fun completeAuth(email: String? = null, firstName: String? = null, lastName: String? = null)
    suspend fun setDarkTheme(dark: Boolean?)
    suspend fun setPendingSketchJobId(jobId: String?)
    suspend fun getPendingSketchJobId(): String?
}
