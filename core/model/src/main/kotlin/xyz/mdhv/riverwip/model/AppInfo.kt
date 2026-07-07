package xyz.mdhv.riverwip.model

/**
 * The single runtime reference for the app's package base.
 *
 * The final app name and package are **RESERVED** (see STATE.md §RESERVED). Until
 * then the working base lives in exactly two places: `riverwip.packageBase` in
 * `gradle.properties` (drives every module namespace + the applicationId) and
 * this constant (drives runtime references such as ContentProvider authorities
 * and DataStore file names). The late rename sweep edits those two spots only.
 */
object AppInfo {
    const val PACKAGE_BASE: String = "xyz.mdhv.riverwip"

    /** Working product name. RESERVED — never surface a final name in product copy. */
    const val WORKING_NAME: String = "river"
}
