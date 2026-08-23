package com.homedesign.android.domain.project

import com.homedesign.android.domain.geom.RoomGeometry
import com.homedesign.android.domain.model.Home
import kotlinx.coroutines.flow.Flow

/**
 * Dashboard + editor persistence: meta list, Home archive blobs, thumbnails.
 * Replaces the split `domain.io.ProjectRepository` / catalog interfaces.
 */
data class ProjectMeta(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val roomCount: Int = 0,
    val wallCount: Int = 0,
    val levelCount: Int = 1,
    val floorAreaM2: Double = 0.0,
)

interface ProjectRepository {
    fun observeProjects(): Flow<List<ProjectMeta>>
    suspend fun listProjects(): List<ProjectMeta>
    suspend fun getProject(id: String): ProjectMeta?

    /** Empty Home via [com.homedesign.android.domain.model.HomeFactory], encoded to `.homedesign`. */
    suspend fun createBlank(name: String = "Untitled"): ProjectMeta

    /** Persist a decoded Home (e.g. sketch import) as a new project. */
    suspend fun createFromHome(home: Home, name: String? = null, thumbnail: ByteArray? = null): ProjectMeta

    suspend fun loadHome(id: String): Home
    suspend fun saveHome(id: String, home: Home, thumbnailJpegBytes: ByteArray? = null)

    suspend fun rename(id: String, name: String)
    suspend fun delete(id: String)
    suspend fun touch(id: String)
}

/** Σ room-polygon areas (shoelace), cm² → m². */
fun floorAreaM2(home: Home): Double {
    var totalCm2 = 0.0
    for (room in home.rooms) {
        totalCm2 += RoomGeometry.polygonArea(room)
    }
    return totalCm2 / 10_000.0
}

fun projectMetaFromHome(
    id: String,
    home: Home,
    createdAt: Long,
    updatedAt: Long,
): ProjectMeta = ProjectMeta(
    id = id,
    name = home.name ?: "Untitled",
    createdAt = createdAt,
    updatedAt = updatedAt,
    roomCount = home.rooms.size,
    wallCount = home.walls.size,
    levelCount = home.levels.size.coerceAtLeast(1),
    floorAreaM2 = floorAreaM2(home),
)
