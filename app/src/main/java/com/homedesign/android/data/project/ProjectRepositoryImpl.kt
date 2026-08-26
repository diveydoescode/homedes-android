package com.homedesign.android.data.project

import android.content.Context
import com.homedesign.android.data.local.db.ProjectDao
import com.homedesign.android.data.local.db.ProjectEntity
import com.homedesign.android.domain.export.PlanThumbnail
import com.homedesign.android.domain.io.HomedesignZip
import com.homedesign.android.domain.model.Home
import com.homedesign.android.domain.model.HomeFactory
import com.homedesign.android.domain.project.ProjectMeta
import com.homedesign.android.domain.project.ProjectRepository
import com.homedesign.android.domain.project.projectMetaFromHome
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ProjectRepositoryImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val dao: ProjectDao,
) : ProjectRepository {

    override fun observeProjects(): Flow<List<ProjectMeta>> =
        dao.observeAll().map { rows -> rows.map { it.toMeta() } }

    override suspend fun listProjects(): List<ProjectMeta> =
        dao.listAll().map { it.toMeta() }

    override suspend fun getProject(id: String): ProjectMeta? =
        dao.getById(id)?.toMeta()

    override suspend fun createBlank(name: String): ProjectMeta {
        val home = HomeFactory.emptyHome(name)
        return persistNew(home, name = name, thumbnail = null)
    }

    override suspend fun createFromHome(
        home: Home,
        name: String?,
        thumbnail: ByteArray?,
    ): ProjectMeta {
        val resolved = name ?: home.name ?: "Untitled"
        val named = if (home.name == resolved) home else home.copy(name = resolved)
        return persistNew(named, name = resolved, thumbnail = thumbnail)
    }

    override suspend fun loadHome(id: String): Home {
        val entity = dao.getById(id) ?: error("Project not found: $id")
        val bytes = entity.archiveBlob
            ?: return HomeFactory.emptyHome(entity.name)
        return HomedesignZip.decode(bytes, HomedesignZip.embeddedTextureDirectory(appContext.filesDir))
    }

    override suspend fun saveHome(id: String, home: Home, thumbnailJpegBytes: ByteArray?) {
        val existing = dao.getById(id) ?: error("Project not found: $id")
        val now = System.currentTimeMillis()
        val archive = HomedesignZip.encode(home)
        val thumb = thumbnailJpegBytes ?: bestEffortThumb(home) ?: existing.thumbnailBlob
        val meta = projectMetaFromHome(id, home, existing.createdAt, now, thumbnailJpeg = thumb)
        dao.upsert(
            existing.copy(
                name = meta.name,
                updatedAt = now,
                roomCount = meta.roomCount,
                wallCount = meta.wallCount,
                levelCount = meta.levelCount,
                floorAreaM2 = meta.floorAreaM2,
                archiveBlob = archive,
                thumbnailBlob = thumb,
            ),
        )
    }

    override suspend fun rename(id: String, name: String) {
        val trimmed = name.trim().ifBlank { return }
        val existing = dao.getById(id) ?: return
        val now = System.currentTimeMillis()
        val bytes = existing.archiveBlob
        if (bytes != null) {
            val home = HomedesignZip.decode(
                bytes,
                HomedesignZip.embeddedTextureDirectory(appContext.filesDir),
            ).copy(name = trimmed)
            val archive = HomedesignZip.encode(home)
            dao.upsert(
                existing.copy(
                    name = trimmed,
                    updatedAt = now,
                    archiveBlob = archive,
                ),
            )
        } else {
            dao.rename(id, trimmed, now)
        }
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }

    override suspend fun touch(id: String) {
        dao.touch(id, System.currentTimeMillis())
    }

    override suspend fun refreshThumbnails() {
        val dir = HomedesignZip.embeddedTextureDirectory(appContext.filesDir)
        for (entity in dao.listAll()) {
            val bytes = entity.archiveBlob ?: continue
            val home = runCatching { HomedesignZip.decode(bytes, dir) }.getOrNull() ?: continue
            val thumb = bestEffortThumb(home) ?: continue
            dao.upsert(entity.copy(thumbnailBlob = thumb))
        }
    }

    private suspend fun persistNew(
        home: Home,
        name: String,
        thumbnail: ByteArray?,
    ): ProjectMeta {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val named = home.copy(name = name)
        val archive = HomedesignZip.encode(named)
        val thumb = thumbnail ?: bestEffortThumb(named)
        val meta = projectMetaFromHome(id, named, now, now, thumbnailJpeg = thumb)
        dao.upsert(
            ProjectEntity(
                id = id,
                name = meta.name,
                createdAt = now,
                updatedAt = now,
                roomCount = meta.roomCount,
                wallCount = meta.wallCount,
                levelCount = meta.levelCount,
                floorAreaM2 = meta.floorAreaM2,
                archiveBlob = archive,
                thumbnailBlob = thumb,
            ),
        )
        return meta
    }

    private fun bestEffortThumb(home: Home): ByteArray? =
        runCatching { PlanThumbnail.renderJpeg(home) }.getOrNull()
}
