package xyz.mdhv.riverwip.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import xyz.mdhv.riverwip.AppLocale
import xyz.mdhv.riverwip.RiverApplication

/**
 * Keeps the home-screen widgets in step with the app's data, by push rather
 * than by polling: `updatePeriodMillis` is 0 on every widget.
 *
 * Three things redraw them:
 *  - the fetch worker, at the end of every run, directly (see AppContainer),
 *    so a background fetch is on the home screen before the process can go;
 *  - any write to a table a widget draws from, wherever in the app it came
 *    from (a read, a clipping, a source switched off, a pull to refresh),
 *    through Room's own invalidation signal, debounced so a fetch that writes
 *    a hundred rows redraws once;
 *  - the two settings outside the database that a widget shows: Today in
 *    History switched on or off, and its column arriving for a new day.
 *
 * Nothing here runs a query unless at least one widget is on a home screen.
 */
internal object WidgetUpdater {

    /** Quiet period before a burst of writes becomes one redraw. */
    private const val DEBOUNCE_MS = 1_500L

    /**
     * A widget must never take the app down: this scope starts in every
     * process, including one started only for a fetch or a widget broadcast,
     * so anything that escapes (a dead system server, a corrupt settings file)
     * is dropped here rather than reaching the thread's crash handler. Nothing
     * is logged or sent anywhere.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, _ -> })
    private val lock = Any()
    private var pending: Job? = null

    @Volatile
    private var started = false

    /** Once per process, from [RiverApplication.onCreate]. */
    fun start(app: RiverApplication) {
        if (started) return
        started = true
        scope.launch {
            // Room's tracker must be registered off the main thread.
            try {
                app.container.addDataChangeListener { request(app) }
            } catch (e: Exception) {
                // Without it the widgets still redraw after every fetch and on
                // their own boundaries; they just miss in-app changes.
            }
        }
        scope.launch {
            app.container.settingsRepository.observeSettings()
                .map { it.todayInHistoryEnabled }
                .distinctUntilChanged()
                .drop(1)
                .catch { }
                .collect { request(app) }
        }
        scope.launch {
            app.container.todayInHistoryRepository.observeCachedColumn()
                .map { it?.date }
                .distinctUntilChanged()
                .drop(1)
                .catch { }
                .collect { request(app) }
        }
    }

    /** Redraw soon, once whatever is writing has paused. Safe from any thread. */
    fun request(context: Context) {
        val app = context.applicationContext
        synchronized(lock) {
            pending?.cancel()
            pending = scope.launch {
                delay(DEBOUNCE_MS)
                updateAll(app)
            }
        }
    }

    /** For broadcast receivers, which hand their `goAsync()` result in and finish it themselves. */
    fun launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)

    /** Redraw every placed Nooz widget now, then set the next date-boundary alarm. */
    suspend fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        var any = false
        for (kind in NoozWidget.entries) {
            val ids = try {
                manager.getAppWidgetIds(ComponentName(context, kind.provider))
            } catch (e: RuntimeException) {
                continue
            }
            if (ids.isEmpty()) continue
            any = true
            update(context, kind, ids)
        }
        if (any) WidgetBoundary.schedule(context) else WidgetBoundary.cancel(context)
    }

    /** Redraw the given widgets of one kind. */
    suspend fun update(context: Context, kind: NoozWidget, ids: IntArray) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app) ?: return
        val container = (app as RiverApplication).container
        // The reader's chosen language, which a bare receiver context misses
        // below API 33 (AppLocale mirrors it there; above, the platform does).
        val localized = AppLocale.wrap(app)
        val now = System.currentTimeMillis()
        for (id in ids) {
            val views = try {
                WidgetContent.render(localized, container, kind, id, now)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                WidgetContent.error(localized, kind, id)
            }
            try {
                manager.updateAppWidget(id, views)
            } catch (e: RuntimeException) {
                // The update could not be delivered (too large for the binder, or
                // the system server is gone). Try the plain error state, which has
                // no pictures in it; if that fails too, the old drawing stays.
                try {
                    manager.updateAppWidget(id, WidgetContent.error(localized, kind, id))
                } catch (e: RuntimeException) {
                    // Nothing more a widget can do; never crash the app over it.
                }
            }
        }
    }

    fun anyPlaced(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        return try {
            NoozWidget.entries.any { manager.getAppWidgetIds(ComponentName(context, it.provider)).isNotEmpty() }
        } catch (e: RuntimeException) {
            // Unknown: keep the boundary alarm rather than cancel it on a guess.
            true
        }
    }
}
