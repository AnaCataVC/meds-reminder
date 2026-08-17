package com.medsreminder

import android.app.Application
import com.medsreminder.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * Application class initializing the Koin dependency injection container.
 */
class MedsApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@MedsApplication)
            modules(appModule)
        }
    }
}
