package xyz.mdhv.riverwip.widget

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Where a widget tap lands inside the app. */
enum class WidgetDestination {
    /** The Stand (the reading list). */
    STAND,

    /** One article in the reader; falls back to the Stand if it has aged out of storage. */
    ITEM,

    /** A clipped article in the reader; falls back to the Clippings shelf if it has aged out. */
    CLIPPING,

    /** Edit › Region & Topics, where the cross-section explains every number with its rule. */
    CROSS_SECTION,

    /**
     * Edit › Sources, where sources are added, switched off and removed. It
     * does not explain the health states, so the health widget's own words
     * carry their rules ("No fetch in 48 hours").
     */
    SOURCES,

    /** Edit › Settings, where Today in History is switched on and off. */
    SETTINGS,

    /** The Clippings shelf. */
    CLIPPINGS,
}

/** A validated request from a widget tap. */
data class WidgetRequest(val destination: WidgetDestination, val itemId: String? = null)

/**
 * Taps from the home screen. Every tap is an explicit, immutable intent to
 * [TAP_ALIAS], a non-exported alias of MainActivity; there is no URL scheme.
 *
 * MainActivity itself has to be exported (it is the launcher entry), so any
 * app could start it with these extras. Opening an article records a read,
 * so a request is honoured only when it arrived through the alias, which no
 * other app can start; the widgets' PendingIntents run as Nooz and can.
 */
object WidgetLinks {

    const val ACTION_OPEN = "xyz.mdhv.riverwip.widget.OPEN"
    const val EXTRA_DESTINATION = "xyz.mdhv.riverwip.widget.DESTINATION"
    const val EXTRA_ITEM_ID = "xyz.mdhv.riverwip.widget.ITEM_ID"

    /** The `<activity-alias>` in AndroidManifest.xml that widget taps go through. Not exported. */
    const val TAP_ALIAS = "xyz.mdhv.riverwip.widget.WidgetTap"

    /** Tap slots per widget: 0 is the whole widget, 1..5 are rows. */
    const val SLOTS = 8

    /** Item ids are SHA-derived hex strings; anything far longer is not one of ours. */
    private const val MAX_ITEM_ID_LENGTH = 256

    fun open(
        context: Context,
        appWidgetId: Int,
        slot: Int,
        destination: WidgetDestination,
        itemId: String? = null,
    ): PendingIntent {
        val intent = Intent()
            .setComponent(ComponentName(context.packageName, TAP_ALIAS))
            .setAction(ACTION_OPEN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_DESTINATION, destination.name)
        if (itemId != null) intent.putExtra(EXTRA_ITEM_ID, itemId)
        return PendingIntent.getActivity(
            context,
            requestCode(appWidgetId, slot),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Unique per widget id and tap slot, so one widget's rows never overwrite another's. */
    fun requestCode(appWidgetId: Int, slot: Int): Int = appWidgetId * SLOTS + slot.coerceIn(0, SLOTS - 1)

    /**
     * The widget request in [intent], or null when it is not one: the wrong
     * action, a component other than [TAP_ALIAS] (another app starting
     * MainActivity directly), or Recents relaunching a task a widget tap once
     * started, which would act on the same tap a second time.
     */
    fun parse(intent: Intent?): WidgetRequest? {
        if (intent?.action != ACTION_OPEN) return null
        if (!isTrusted(intent.component?.className, intent.flags)) return null
        return parse(intent.getStringExtra(EXTRA_DESTINATION), intent.getStringExtra(EXTRA_ITEM_ID))
    }

    /** True for a tap that came through [TAP_ALIAS] and is not a relaunch from Recents. */
    fun isTrusted(componentClass: String?, flags: Int): Boolean =
        componentClass == TAP_ALIAS && flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0

    /**
     * Validates a tap's extras. Anything unknown is ignored rather than
     * guessed at: an unknown destination, or an article destination with no
     * plausible item id, opens nothing special and the app starts as usual.
     */
    fun parse(destination: String?, itemId: String?): WidgetRequest? {
        val dest = WidgetDestination.entries.firstOrNull { it.name == destination } ?: return null
        return when (dest) {
            WidgetDestination.ITEM, WidgetDestination.CLIPPING -> {
                val id = itemId?.trim()
                if (id.isNullOrEmpty() || id.length > MAX_ITEM_ID_LENGTH) null else WidgetRequest(dest, id)
            }
            else -> WidgetRequest(dest)
        }
    }
}
