package com.homedesign.android.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.homedesign.android.domain.project.ProjectMeta

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val roomCount: Int = 0,
    val wallCount: Int = 0,
    val levelCount: Int = 1,
    val floorAreaM2: Double = 0.0,
    val archiveBlob: ByteArray? = null,
    val thumbnailBlob: ByteArray? = null,
) {
    fun toMeta(): ProjectMeta = ProjectMeta(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        roomCount = roomCount,
        wallCount = wallCount,
        levelCount = levelCount,
        floorAreaM2 = floorAreaM2,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ProjectEntity
        return id == other.id &&
            name == other.name &&
            createdAt == other.createdAt &&
            updatedAt == other.updatedAt &&
            roomCount == other.roomCount &&
            wallCount == other.wallCount &&
            levelCount == other.levelCount &&
            floorAreaM2 == other.floorAreaM2 &&
            archiveBlob.contentEquals(other.archiveBlob) &&
            thumbnailBlob.contentEquals(other.thumbnailBlob)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + roomCount
        result = 31 * result + wallCount
        result = 31 * result + levelCount
        result = 31 * result + floorAreaM2.hashCode()
        result = 31 * result + (archiveBlob?.contentHashCode() ?: 0)
        result = 31 * result + (thumbnailBlob?.contentHashCode() ?: 0)
        return result
    }
}
