package xyz.mdhv.riverwip

import android.app.Application

/**
 * Application / composition root.
 *
 * House style is **manual DI** (constructor injection, no Hilt/Koin). The
 * object graph is assembled here in [AppContainer] and handed down to features.
 * P0 keeps it empty; each phase adds its dependencies to the container.
 */
class RiverApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
