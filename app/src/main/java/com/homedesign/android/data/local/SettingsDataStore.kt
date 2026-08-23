package com.homedesign.android.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.homedesign.android.domain.settings.SettingsRepository
import com.homedesign.android.domain.settings.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "hd_settings",
)

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext context: Context,
) : SettingsRepository {

    private val store = context.settingsDataStore

    private object Keys {
        val hasOnboarded = booleanPreferencesKey("has_onboarded")
        val firstName = stringPreferencesKey("first_name")
        val lastName = stringPreferencesKey("last_name")
        val email = stringPreferencesKey("email")
        val useMetric = booleanPreferencesKey("use_metric")
        val darkTheme = stringPreferencesKey("dark_theme") // "true" | "false" | absent = system
        val pendingSketchJob = stringPreferencesKey("pending_sketch_job")
    }

    override val settings: Flow<UserSettings> = store.data.map { prefs ->
        prefs.toSettings()
    }

    override suspend fun getSettings(): UserSettings = settings.first()

    override suspend fun setIdentity(firstName: String, lastName: String) {
        store.edit { prefs ->
            prefs[Keys.firstName] = firstName
            prefs[Keys.lastName] = lastName
        }
    }

    override suspend fun setUseMetric(useMetric: Boolean) {
        store.edit { prefs -> prefs[Keys.useMetric] = useMetric }
    }

    override suspend fun setEmail(email: String) {
        store.edit { prefs -> prefs[Keys.email] = email }
    }

    override suspend fun completeAuth(
        email: String?,
        firstName: String?,
        lastName: String?,
    ) {
        store.edit { prefs ->
            prefs[Keys.hasOnboarded] = true
            if (email != null) prefs[Keys.email] = email
            if (firstName != null) prefs[Keys.firstName] = firstName
            if (lastName != null) prefs[Keys.lastName] = lastName
        }
    }

    override suspend fun setDarkTheme(dark: Boolean?) {
        store.edit { prefs ->
            if (dark == null) {
                prefs.remove(Keys.darkTheme)
            } else {
                prefs[Keys.darkTheme] = dark.toString()
            }
        }
    }

    override suspend fun setPendingSketchJobId(jobId: String?) {
        store.edit { prefs ->
            if (jobId.isNullOrBlank()) {
                prefs.remove(Keys.pendingSketchJob)
            } else {
                prefs[Keys.pendingSketchJob] = jobId
            }
        }
    }

    override suspend fun getPendingSketchJobId(): String? =
        store.data.first()[Keys.pendingSketchJob]

    private fun Preferences.toSettings(): UserSettings {
        val darkRaw = this[Keys.darkTheme]
        return UserSettings(
            hasOnboarded = this[Keys.hasOnboarded] ?: false,
            firstName = this[Keys.firstName].orEmpty(),
            lastName = this[Keys.lastName].orEmpty(),
            email = this[Keys.email].orEmpty(),
            useMetric = this[Keys.useMetric] ?: true,
            darkTheme = when (darkRaw) {
                "true" -> true
                "false" -> false
                else -> null
            },
        )
    }
}
