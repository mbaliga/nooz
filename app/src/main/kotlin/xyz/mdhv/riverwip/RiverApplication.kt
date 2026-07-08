package xyz.mdhv.riverwip

import android.app.Application
import androidx.work.Configuration
import xyz.mdhv.riverwip.data.work.FetchScheduler

/**
 * Application / composition root.
 *
 * House style is **manual DI** (constructor injection, no Hilt/Koin). The object
 * graph is assembled in [AppContainer] and handed down to features.
 *
 * [container] is built lazily rather than in `onCreate()`: WorkManager's default
 * initializer is a `ContentProvider` that runs *before* `Application.onCreate()`,
 * and it detects [Configuration.Provider] by reading [workManagerConfiguration]
 * at that point. If [container] were only built in `onCreate()`, that read could
 * happen first and crash (or silently fall back to a default config without our
 * [xyz.mdhv.riverwip.data.work.RiverWorkerFactory]). `by lazy` guarantees
 * construction on first touch regardless of which caller reaches it first — and
 * by the time any content provider runs, `attachBaseContext` has already
 * completed, so `this` is a valid [android.content.Context].
 */
class RiverApplication : Application(), Configuration.Provider {

    val container: AppContainer by lazy { AppContainer(this) }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(container.workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        FetchScheduler.schedule(this)
    }
}
