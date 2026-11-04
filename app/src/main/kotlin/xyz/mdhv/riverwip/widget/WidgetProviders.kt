package xyz.mdhv.riverwip.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.withTimeoutOrNull
import xyz.mdhv.riverwip.model.WidgetClock
import java.time.ZoneId

/**
 * The base of every Nooz home-screen widget. Exported, as every widget
 * provider must be so that launchers can bind it; it acts only on the
 * platform's own widget broadcasts and the three clock and locale broadcasts,
 * and everything it draws comes from data already on the device.
 */
abstract class NoozWidgetProvider : AppWidgetProvider() {

    internal abstract val kind: NoozWidget

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refresh(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // The time, the zone or the phone's language changed: dates, "as
            // of" stamps and words may all read differently now.
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED -> refresh(context, null)
            else -> super.onReceive(context, intent)
        }
    }

    override fun onEnabled(context: Context) {
        WidgetBoundary.schedule(context)
    }

    override fun onDisabled(context: Context) {
        if (!WidgetUpdater.anyPlaced(context)) WidgetBoundary.cancel(context)
    }

    /** Redraws [ids], or every placed widget of this kind when [ids] is null. */
    private fun refresh(context: Context, ids: IntArray?) {
        // The provider is exported, so its broadcast can come from any app with
        // any ids in it. Only this provider's own placed widgets are drawn:
        // another widget's id would otherwise get this widget's content.
        val manager = AppWidgetManager.getInstance(context) ?: return
        val own = try {
            manager.getAppWidgetIds(ComponentName(context, javaClass))
        } catch (e: RuntimeException) {
            return
        }
        val mine = ids?.filter { it in own }?.toIntArray() ?: own
        if (mine.isEmpty()) return
        val result = goAsync()
        val app = context.applicationContext
        WidgetUpdater.launch {
            try {
                // A broadcast has about ten seconds; give up well before.
                withTimeoutOrNull(TIMEOUT_MS) {
                    WidgetUpdater.update(app, kind, mine)
                    WidgetBoundary.schedule(app)
                }
            } finally {
                result.finish()
            }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 8_000L
    }
}

/** Flowed and read, this week: stream share against read share by topic, as a picture (internally, "the river"). */
class RiverWidgetProvider : NoozWidgetProvider() {
    override val kind: NoozWidget get() = NoozWidget.RIVER
}

/** Headlines from your sources, newest first, ending in an end-of-list mark. */
class HeadlinesWidgetProvider : NoozWidgetProvider() {
    override val kind: NoozWidget get() = NoozWidget.HEADLINES
}

/** Today in History, from the column the Stand already loaded. */
class HistoryWidgetProvider : NoozWidgetProvider() {
    override val kind: NoozWidget get() = NoozWidget.HISTORY
}

/** Source health: how many of your sources fetch normally, and which do not. */
class SourceHealthWidgetProvider : NoozWidgetProvider() {
    override val kind: NoozWidget get() = NoozWidget.HEALTH
}

/** Clippings, most recently clipped first. */
class ClippingsWidgetProvider : NoozWidgetProvider() {
    override val kind: NoozWidget get() = NoozWidget.CLIPPINGS
}

/** Coverage and breadth: the cross-section's numbers as a plain record. */
class CoverageWidgetProvider : NoozWidgetProvider() {
    override val kind: NoozWidget get() = NoozWidget.COVERAGE
}

/**
 * One windowed alarm at the next moment widget content changes with time
 * alone (the date turning over for Today in History, the river's week turning
 * over). Inexact and non-waking, so no exact-alarm permission: it is
 * delivered within [WINDOW_MS] after the boundary while the phone is awake
 * (a plain inexact alarm may come up to an hour late), and a sleeping phone is
 * not woken for it; the widget catches up the next time the phone is awake.
 * The "as of" stamps carry their date, so they stay true even if it is late.
 */
internal object WidgetBoundary {

    const val ACTION_REFRESH = "xyz.mdhv.riverwip.widget.REFRESH"

    /** Android 12's shortest allowed window for an inexact alarm. */
    private const val WINDOW_MS = 10L * 60_000L

    fun schedule(context: Context) {
        try {
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            val at = WidgetClock.nextBoundary(System.currentTimeMillis(), ZoneId.systemDefault())
            alarms.setWindow(AlarmManager.RTC, at, WINDOW_MS, pendingIntent(context))
        } catch (e: RuntimeException) {
            // The widgets still redraw after every fetch and data change.
        }
    }

    fun cancel(context: Context) {
        try {
            context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
        } catch (e: RuntimeException) {
            // Nothing to undo if the alarm service is unreachable.
        }
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, WidgetRefreshReceiver::class.java).setAction(ACTION_REFRESH),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Receives [WidgetBoundary]'s alarm. Not exported: only this app's own alarm reaches it. */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WidgetBoundary.ACTION_REFRESH) return
        val result = goAsync()
        val app = context.applicationContext
        WidgetUpdater.launch {
            try {
                withTimeoutOrNull(8_000L) { WidgetUpdater.updateAll(app) }
            } finally {
                result.finish()
            }
        }
    }
}
