package xyz.mdhv.riverwip.crash

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Device-only crash capture, ported from the Hyle Design System's
 * `crash-recovery` module (owner's #17). Faithful to that module's philosophy:
 * an uncaught-exception handler writes a single report to the app's private
 * files dir and **nothing is ever transmitted** — no analytics, no network, no
 * third-party SDK. The report waits there until the user reads it (Settings ›
 * "Last crash"), copies/exports it, or clears it.
 *
 * Every operation is `runCatching`-guarded so the handler can never itself
 * crash. CI never sees these — CI runs unit tests, never launches the app.
 */
object CrashRecovery {
    private const val FILE_NAME = "crash_report.txt"

    /** Install the handler once, from [Application.onCreate]. Chains to any prior handler. */
    fun install(app: Application, appLabel: String) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { capture(app, appLabel, throwable, thread.name) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** For a synchronous init failure caught in your own `catch` block. */
    fun captureInitError(context: Context, appLabel: String, throwable: Throwable) {
        runCatching { capture(context, appLabel, throwable, Thread.currentThread().name) }
    }

    private fun capture(context: Context, appLabel: String, throwable: Throwable, threadName: String) {
        val report = CrashReport.of(appLabel, System.currentTimeMillis(), threadName, throwable, deviceInfo(context))
        file(context).writeText(report.encode())
        android.util.Log.e("CrashRecovery", "captured crash for $appLabel", throwable)
    }

    @Suppress("DEPRECATION")
    private fun legacyVersionCode(info: android.content.pm.PackageInfo): Long = info.versionCode.toLong()

    private fun deviceInfo(context: Context): CrashReport.DeviceInfo = runCatching {
        val pm = context.applicationContext.packageManager
        val info = pm.getPackageInfo(context.applicationContext.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else legacyVersionCode(info)
        CrashReport.DeviceInfo(
            appVersionName = info.versionName,
            appVersionCode = versionCode,
            osSdkInt = Build.VERSION.SDK_INT,
            deviceManufacturer = Build.MANUFACTURER ?: "?",
            deviceModel = Build.MODEL ?: "?",
        )
    }.getOrDefault(CrashReport.DeviceInfo(null, null, Build.VERSION.SDK_INT, "?", "?"))

    /** Non-null if a crash was captured and not yet cleared. */
    fun pending(context: Context): CrashReport.Decoded? = runCatching {
        file(context).takeIf { it.exists() }?.readText()?.let(CrashReport::decode)
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun file(context: Context): File = File(context.applicationContext.filesDir, FILE_NAME)
}

/**
 * A captured crash (ported from Hyle's `CrashReport`). [headline] is the one
 * line worth reading first; [device] is reproduction metadata; [trace] is the
 * full stack, kept separate so a UI can hide it behind a details toggle.
 */
data class CrashReport(
    val appLabel: String,
    val whenMillis: Long,
    val threadName: String,
    val headline: String,
    val device: DeviceInfo,
    val trace: String,
) {
    data class DeviceInfo(
        val appVersionName: String?,
        val appVersionCode: Long?,
        val osSdkInt: Int,
        val deviceManufacturer: String,
        val deviceModel: String,
    )

    fun render(): String = buildString {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        append(appLabel).append(" crash\n")
        append("when: ").append(format.format(Date(whenMillis))).append('\n')
        append("thread: ").append(threadName).append('\n')
        append("app version: ").append(device.appVersionName ?: "?")
        append(" (").append(device.appVersionCode?.toString() ?: "?").append(")\n")
        append("device: ").append(device.deviceManufacturer).append(' ').append(device.deviceModel)
        append(" · Android SDK ").append(device.osSdkInt).append("\n\n")
        append(headline).append("\n\n")
        append(trace)
    }

    fun encode(): String = "$headline\n\n${render()}"

    companion object {
        fun headlineOf(throwable: Throwable): String {
            val type = throwable.javaClass.simpleName.ifBlank { throwable.javaClass.name }
            val message = throwable.message?.takeIf { it.isNotBlank() }
            return if (message != null) "$type: $message" else type
        }

        fun stackTraceOf(throwable: Throwable): String =
            StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()

        fun of(
            appLabel: String,
            whenMillis: Long,
            threadName: String,
            throwable: Throwable,
            device: DeviceInfo,
        ): CrashReport = CrashReport(
            appLabel = appLabel,
            whenMillis = whenMillis,
            threadName = threadName,
            headline = headlineOf(throwable),
            device = device,
            trace = stackTraceOf(throwable),
        )

        fun decode(persisted: String): Decoded {
            val separator = "\n\n"
            val splitAt = persisted.indexOf(separator)
            return if (splitAt >= 0) {
                Decoded(persisted.substring(0, splitAt), persisted.substring(splitAt + separator.length))
            } else {
                Decoded(persisted.lines().firstOrNull().orEmpty(), persisted)
            }
        }
    }

    data class Decoded(val headline: String, val fullReport: String)
}
