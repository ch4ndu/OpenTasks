package com.udnahc.opentasks

import android.app.Application
import com.udnahc.opentasks.di.initKoin
import org.koin.android.ext.koin.androidContext

class OpenTasksApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@OpenTasksApplication)
        }
    }
}
