package com.example.android_tv_remote.di

import android.app.Application
import com.example.android_tv_remote.BuildConfig
import com.example.android_tv_remote.core.utils.PrefsUtils
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class App: Application() {
    override fun onCreate() {
        super.onCreate()

        PrefsUtils.init(this)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}