package com.homedesign.android.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.homedesign.android.domain.catalog.RECENT_CAP
import com.homedesign.android.domain.catalog.recordRecent
import com.homedesign.android.domain.model.UnitSystem
import com.homedesign.android.domain.settings.SettingsRepository
import com.homedesign.android.domain.settings.UserSettings
import com.homedesign.android.domain.sketch.PendingSketchJob
import com.homedesign.android.domain.sketch.encodePendingSketchJob
import com.homedesign.android.domain.sketch.parsePendingSketchJob
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

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
        /** Legacy boolean — migrated into [unitSystem]. */
        val useMetric = booleanPreferencesKey("use_metric")
        val unitSystem = stringPreferencesKey("unit_system")
        val darkTheme = stringPreferencesKey("dark_theme") // "true" | "false" | absent = system
        val pendingSketchJob = stringPreferencesKey("pending_sketch_job")
        val recentFurniture = stringPreferencesKey("recent_furniture_catalog_ids")
        val editorTipDismissed = booleanPreferencesKey("editor_tip_dismissed")
        val lastProjectId = stringPreferencesKey("last_project_id")
        val editorSessionDirty = booleanPreferencesKey("editor_session_dirty")
        /** iOS `hd.editing.orthoLock` — default ON when absent. */
        val orthoLock = booleanPreferencesKey("ortho_lock")
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
        setUnitSystem(if (useMetric) UnitSystem.Metric else UnitSystem.Imperial)
    }

    override suspend fun setUnitSystem(unitSystem: UnitSystem) {
        store.edit { prefs ->
            prefs[Keys.unitSystem] = unitSystem.value
            prefs[Keys.useMetric] = unitSystem != UnitSystem.Imperial
        }
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
        if (jobId.isNullOrBlank()) {
            setPendingSketchJob(null)
        } else {
            setPendingSketchJob(
                PendingSketchJob(jobId = jobId, startedAt = System.currentTimeMillis()),
            )
        }
    }

    override suspend fun getPendingSketchJobId(): String? =
        getPendingSketchJob()?.jobId

    override suspend fun setPendingSketchJob(job: PendingSketchJob?) {
        store.edit { prefs ->
            if (job == null || job.jobId.isBlank()) {
                prefs.remove(Keys.pendingSketchJob)
            } else {
                prefs[Keys.pendingSketchJob] = encodePendingSketchJob(job)
            }
        }
    }

    override suspend fun getPendingSketchJob(): PendingSketchJob? {
        val raw = store.data.first()[Keys.pendingSketchJob]
        val parsed = parsePendingSketchJob(raw)
        if (raw != null && parsed == null) {
            store.edit { it.remove(Keys.pendingSketchJob) }
        }
        return parsed
    }

    override suspend fun recordRecentFurniture(catalogId: String) {
        if (catalogId.isBlank()) return
        store.edit { prefs ->
            val next = recordRecent(catalogId, parseRecentIds(prefs[Keys.recentFurniture]), RECENT_CAP)
            prefs[Keys.recentFurniture] = encodeRecentIds(next)
        }
    }

    override suspend fun setEditorTipDismissed(dismissed: Boolean) {
        store.edit { prefs -> prefs[Keys.editorTipDismissed] = dismissed }
    }

    override suspend fun setOrthoLock(enabled: Boolean) {
        store.edit { prefs -> prefs[Keys.orthoLock] = enabled }
    }

    override suspend fun setLastProjectId(projectId: String?) {
        store.edit { prefs ->
            if (projectId.isNullOrBlank()) prefs.remove(Keys.lastProjectId)
            else prefs[Keys.lastProjectId] = projectId
        }
    }

    override suspend fun setEditorSessionDirty(dirty: Boolean) {
        store.edit { prefs -> prefs[Keys.editorSessionDirty] = dirty }
    }

    override suspend fun clearEditorSession() {
        store.edit { prefs ->
            prefs.remove(Keys.editorSessionDirty)
            // Keep lastProjectId for "Continue" hero; only clear dirty flag.
            prefs[Keys.editorSessionDirty] = false
        }
    }

    private fun Preferences.toSettings(): UserSettings {
        val darkRaw = this[Keys.darkTheme]
        return UserSettings(
            hasOnboarded = this[Keys.hasOnboarded] ?: false,
            firstName = this[Keys.firstName].orEmpty(),
            lastName = this[Keys.lastName].orEmpty(),
            email = this[Keys.email].orEmpty(),
            unitSystem = resolveUnitSystem(),
            darkTheme = when (darkRaw) {
                "true" -> true
                "false" -> false
                else -> null
            },
            recentFurnitureCatalogIds = parseRecentIds(this[Keys.recentFurniture]),
            editorTipDismissed = this[Keys.editorTipDismissed] ?: false,
            lastProjectId = this[Keys.lastProjectId],
            editorSessionDirty = this[Keys.editorSessionDirty] ?: false,
            orthoLock = this[Keys.orthoLock] ?: true,
        )
    }

    private fun parseRecentIds(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val parsed = Json.parseToJsonElement(raw)
            if (parsed is JsonArray) {
                parsed.mapNotNull { el -> el.jsonPrimitive.contentOrNull }
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encodeRecentIds(ids: List<String>): String =
        JsonArray(ids.map { JsonPrimitive(it) }).toString()

    private fun Preferences.resolveUnitSystem(): UnitSystem {
        when (this[Keys.unitSystem]) {
            UnitSystem.Millimetre.value, "millimetre", "mm" -> return UnitSystem.Millimetre
            UnitSystem.Metric.value, "metric", "cm" -> return UnitSystem.Metric
            UnitSystem.Imperial.value, "imperial", "ft" -> return UnitSystem.Imperial
        }
        // Migrate legacy boolean
        return if (this[Keys.useMetric] == false) UnitSystem.Imperial else UnitSystem.Millimetre
    }
}
