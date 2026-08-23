package com.homedesign.android.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ProjectEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class HomeDesignDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}
