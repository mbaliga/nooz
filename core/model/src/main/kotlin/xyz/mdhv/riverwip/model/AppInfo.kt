package xyz.mdhv.riverwip.model

/**
 * The single runtime reference for the app's package base.
 *
 * The final package — `dev.asystemofcells.nooz`, as registered in Play Console
 * (decided 2026-07; see STATE.md §RESERVED) — lives in exactly two places:
 * `riverwip.packageBase` in `gradle.properties` (drives every module namespace +
 * the applicationId) and this constant (drives runtime references such as
 * ContentProvider authorities and DataStore file names).
 */
object AppInfo {
    const val PACKAGE_BASE: String = "dev.asystemofcells.nooz"

    /** Working product name. RESERVED — never surface a final name in product copy. */
    const val WORKING_NAME: String = "river"
}
