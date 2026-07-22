package com.bettermifitness.sync

import android.app.Application
import com.bettermifitness.sync.di.initKoin
import com.bettermifitness.sync.di.provideAndroidContext
import com.bettermifitness.sync.strava.StravaConfig

class BetterMiFitnessSyncApp : Application() {
    override fun onCreate() {
        super.onCreate()
        provideAndroidContext(this)
        AutoSyncPlatform.init(this)
        StravaConfig.clientId = BuildConfig.STRAVA_CLIENT_ID
        StravaConfig.clientSecret = BuildConfig.STRAVA_CLIENT_SECRET
        initKoin()
        AutoSyncSchedule.restore()
        AndroidForegroundAutoSync.register()
    }
}
