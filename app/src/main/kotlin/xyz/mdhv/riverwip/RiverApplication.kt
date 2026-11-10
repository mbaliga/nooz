package xyz.mdhv.riverwip

import android.app.Application
import android.os.LocaleList
import androidx.work.Configuration
import androidx.work.WorkManager
import xyz.mdhv.riverwip.crash.CrashRecovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import xyz.mdhv.riverwip.data.net.OfflineMode
import xyz.mdhv.riverwip.data.work.FetchScheduler
import xyz.mdhv.riverwip.widget.WidgetUpdater

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
        // Mirror the persisted Offline toggle into the process-wide gate that
        // HttpClient / the fetch worker / image loading all consult.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            container.settingsRepository.observeSettings()
                .map { it.offlineMode }
                .distinctUntilChanged()
                .collect { OfflineMode.enabled = it }
        }
        // Home-screen widgets redraw when their data changes, not on a timer.
        WidgetUpdater.start(this)
        lastLocales = resources.configuration.locales
        lastFontScale = resources.configuration.fontScale
    }

    private var lastLocales: LocaleList? = null
    private var lastFontScale: Float = 1f

    /**
     * A new interface language (the per-app choice on API 33+, or the phone's
     * own) or a new font size reaches the widgets here: the words change, and
     * a font size moves every widget's size breakpoints. A rotation or a theme
     * change does not redraw them, since the launcher resolves their day and
     * night colours itself.
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.locales != lastLocales || newConfig.fontScale != lastFontScale) {
            lastLocales = newConfig.locales
            lastFontScale = newConfig.fontScale
            WidgetUpdater.request(this)
        }
    }
}
