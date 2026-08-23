package com.homedesign.android.di

import android.content.Context
import androidx.room.Room
import com.homedesign.android.data.local.db.HomeDesignDatabase
import com.homedesign.android.data.local.db.ProjectDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HomeDesignDatabase =
        Room.databaseBuilder(
            context,
            HomeDesignDatabase::class.java,
            "homedesign.db",
        ).fallbackToDestructiveMigration().build()

    @Provides
    fun provideProjectDao(db: HomeDesignDatabase): ProjectDao = db.projectDao()
}
