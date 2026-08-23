package com.homedesign.android.data.project

import com.homedesign.android.data.local.db.ProjectDao
import com.homedesign.android.data.local.db.ProjectEntity
import com.homedesign.android.domain.io.HomedesignZip
import com.homedesign.android.domain.model.Home
import com.homedesign.android.domain.model.HomeFactory
import com.homedesign.android.domain.project.ProjectMeta
import com.homedesign.android.domain.project.ProjectRepository
import com.homedesign.android.domain.project.projectMetaFromHome
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ProjectRepositoryImpl @Inject constructor(
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
        return HomedesignZip.decode(bytes)
    }

    override suspend fun saveHome(id: String, home: Home, thumbnailJpegBytes: ByteArray?) {
        val existing = dao.getById(id) ?: error("Project not found: $id")
        val now = System.currentTimeMillis()
        val archive = HomedesignZip.encode(home)
        val meta = projectMetaFromHome(id, home, existing.createdAt, now)
        dao.upsert(
            existing.copy(
                name = meta.name,
                updatedAt = now,
                roomCount = meta.roomCount,
                wallCount = meta.wallCount,
                levelCount = meta.levelCount,
                floorAreaM2 = meta.floorAreaM2,
                archiveBlob = archive,
                thumbnailBlob = thumbnailJpegBytes ?: existing.thumbnailBlob,
            ),
        )
    }

    override suspend fun rename(id: String, name: String) {
        dao.rename(id, name, System.currentTimeMillis())
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }

    override suspend fun touch(id: String) {
        dao.touch(id, System.currentTimeMillis())
    }

    private suspend fun persistNew(
        home: Home,
        name: String,
        thumbnail: ByteArray?,
    ): ProjectMeta {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val archive = HomedesignZip.encode(home)
        val meta = projectMetaFromHome(id, home.copy(name = name), now, now)
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
                thumbnailBlob = thumbnail,
            ),
        )
        return meta
    }
}
