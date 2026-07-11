package xyz.mdhv.riverwip

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import xyz.mdhv.riverwip.crash.CrashRecovery
import xyz.mdhv.riverwip.data.work.FetchScheduler

/**
 * Application / composition root.
 *
 * House style is **manual DI** (constructor injection, no Hilt/Koin). The object
 * graph is assembled in [AppContainer] and handed down to features.
 *
 * WorkManager is initialized **on demand** here rather than via its default
 * `ContentProvider` auto-init: the manifest removes
 * `androidx.work.WorkManagerInitializer` (Android Lint's
 * `RemoveWorkManagerInitializer` check requires this once a custom
 * [Configuration] with our own [xyz.mdhv.riverwip.data.work.RiverWorkerFactory]
 * is needed), and [onCreate] calls [WorkManager.initialize] explicitly, after
 * [container] exists. This sidesteps any ordering question entirely — no risk
 * of WorkManager reading our config before the DI container is built, since we
 * are the ones calling `initialize()`, in the order we choose.
 */
class RiverApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Device-only crash capture (Hyle crash-recovery). Installed first so a
        // throw from the DI graph or WorkManager init is still captured.
        CrashRecovery.install(this, appLabel = "Nooz")
        container = AppContainer(this)
        WorkManager.initialize(this, Configuration.Builder().setWorkerFactory(container.workerFactory).build())
        FetchScheduler.schedule(this)
    }
}
