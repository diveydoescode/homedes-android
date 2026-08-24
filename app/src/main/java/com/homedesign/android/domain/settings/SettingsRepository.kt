package com.homedesign.android.domain.settings

import com.homedesign.android.domain.model.UnitSystem
import com.homedesign.android.domain.sketch.PendingSketchJob
import kotlinx.coroutines.flow.Flow

data class UserSettings(
    val hasOnboarded: Boolean = false,
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    /** Preferred display/edit units (plan storage remains cm). Default millimetre — web parity. */
    val unitSystem: UnitSystem = UnitSystem.Millimetre,
    val darkTheme: Boolean? = null,
    /** Most-recent furniture catalog IDs (newest first), capped in [recordRecentFurniture]. */
    val recentFurnitureCatalogIds: List<String> = emptyList(),
    /** First-run editor tip banner dismissed. */
    val editorTipDismissed: Boolean = false,
    /** Last opened project id (for resume-session). */
    val lastProjectId: String? = null,
    /** True when the editor had unsaved edits at last process pause. */
    val editorSessionDirty: Boolean = false,
) {
    /** Legacy helper — true for mm or cm. */
    val useMetric: Boolean get() = unitSystem != UnitSystem.Imperial
}

interface SettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun getSettings(): UserSettings
    suspend fun setIdentity(firstName: String, lastName: String)
    suspend fun setUseMetric(useMetric: Boolean)
    suspend fun setUnitSystem(unitSystem: UnitSystem)
    suspend fun setEmail(email: String)
    suspend fun completeAuth(email: String? = null, firstName: String? = null, lastName: String? = null)
    suspend fun setDarkTheme(dark: Boolean?)
    suspend fun setPendingSketchJobId(jobId: String?)
    suspend fun getPendingSketchJobId(): String?
    suspend fun setPendingSketchJob(job: PendingSketchJob?)
    suspend fun getPendingSketchJob(): PendingSketchJob?
    suspend fun recordRecentFurniture(catalogId: String)
    suspend fun setEditorTipDismissed(dismissed: Boolean)
    suspend fun setLastProjectId(projectId: String?)
    suspend fun setEditorSessionDirty(dirty: Boolean)
    suspend fun clearEditorSession()
}
