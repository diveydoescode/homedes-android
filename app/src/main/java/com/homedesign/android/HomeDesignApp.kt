package com.homedesign.android

import android.app.Application
import com.homedesign.android.domain.catalog.CatalogLoader
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HomeDesignApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CatalogLoader.installFromAssets(this)
    }
}