package com.homedesign.android.di

import com.homedesign.android.data.local.SettingsDataStore
import com.homedesign.android.data.project.ProjectRepositoryImpl
import com.homedesign.android.domain.project.ProjectRepository
import com.homedesign.android.domain.settings.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsDataStore): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindProjectRepository(impl: ProjectRepositoryImpl): ProjectRepository
}
