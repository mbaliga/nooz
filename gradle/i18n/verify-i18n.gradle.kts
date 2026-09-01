// verifyI18n -- the guard that keeps the interface translatable.
//
// WHY NOT LINT
// The obvious answer is Android Lint's `HardcodedText`, and it is the wrong
// one: that check inspects layout XML for `android:text`, and this app has no
// layout XML at all. Enabled at `fatal` it passes a codebase where every
// visible word is a Kotlin literal — an inert guard, which is worse than none,
// because the build goes green and everyone stops looking.
//
// HOW THIS ONE WORKS
// It reads the Kotlin sources for literals sitting in positions that put words
// on the screen — `Text("…")`, `text = "…"`, `contentDescription = "…"` and
// friends — and fails on them.
//
// It is a RATCHET, not a wall. `i18n-allowlist.txt` records, per file, how many
// such literals are still there; the build fails if a file exceeds its recorded
// number, if a file with no entry has any at all, or if a file improves without
// its number being lowered. So:
//   - a brand-new screen is enforced from its first commit,
//   - the migration can land a file at a time without ever going backwards,
//   - and the allowlist is a to-do list that can only shrink.
// When a file reaches zero, delete its line.
//
// The task is a class rather than a `doLast` block on purpose: a closure here
// would capture this script's own functions, which the Gradle configuration
// cache refuses to serialise.

abstract class VerifyI18nTask : DefaultTask() {

    @get:InputFiles
    abstract val sources: ConfigurableFileCollection

    @get:InputFile
    abstract val allowlist: RegularFileProperty

    @get:Input
    abstract val repoRoot: Property<String>

    /**
     * Comments replaced by spaces of the same length, so offsets -- and
     * therefore reported line numbers -- stay exact. KDoc in this codebase
     * quotes example copy constantly; none of it ships.
     */
    private fun blankComments(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        var inString = false
        var inChar = false
        while (i < source.length) {
            val c = source[i]
            val next = if (i + 1 < source.length) source[i + 1] else ' '
            when {
                inString || inChar -> {
                    out.append(c)
                    if (c == '\\') { if (i + 1 < source.length) { out.append(next); i++ } }
                    else if (inString && c == '"') inString = false
                    else if (inChar && c == '\'') inChar = false
                    i++
                }
                c == '"' -> { inString = true; out.append(c); i++ }
                c == '\'' -> { inChar = true; out.append(c); i++ }
                c == '/' && next == '/' -> {
                    while (i < source.length && source[i] != '\n') { out.append(' '); i++ }
                }
                c == '/' && next == '*' -> {
                    while (i < source.length && !(source[i] == '*' && i + 1 < source.length && source[i + 1] == '/')) {
                        out.append(if (source[i] == '\n') '\n' else ' '); i++
                    }
                    repeat(minOf(2, source.length - i)) { out.append(' '); i++ }
                }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }

    companion object {
        /**
         * Words that must stay in English wherever they appear.
         *
         * "Nooz" is the app's own name (STATE.md §RESERVED, the owner's
         * decision) and the two feature names are built on it; a translated
         * masthead is a bug, not a feature. Routing these through resources
         * would mean twenty-nine identical copies of the same word and
         * twenty-nine chances for one to drift. The web guard
         * (web/tests/i18n-coverage.test.mjs) carries the same list for the same
         * reason — a brand that is exempt on one client and not the other is
         * how the two ends up disagreeing.
         */
        val BRAND = setOf("Nooz", "Nooz Flash", "Nooz Cast", "GDELT", "Wikipedia")

        val UI_STRING = Regex(
            """(?x)
            (?:
                \bText\s*\(\s*
              | \btext\s*=\s*
              | \bcontentDescription\s*=\s*
              | \bstateDescription\s*=\s*
              | \bplaceholder\s*=\s*
              | \bsupportingText\s*=\s*
              | \bSectionHeading\s*\(\s*
              | \blabel\s*=\s*
              | \btitle\s*=\s*
            )
            \s*"(?<literal>(?:[^"\\\n]|\\.){2,})"
            """,
        )
    }

    @TaskAction
    fun verify() {
        val root = java.io.File(repoRoot.get())
        val found = linkedMapOf<String, MutableList<Pair<Int, String>>>()
        for (file in sources.files.sortedBy { it.path }) {
            if (!file.isFile || file.extension != "kt") continue
            val rel = file.relativeTo(root).invariantSeparatorsPath
            // Whole-file, not line-by-line. Compose formatting almost always
            // breaks after the opening paren, so `Text(` and its literal live on
            // different lines -- a per-line scan silently missed most of the
            // copy in the app and reported a comfortable, wrong number.
            val text = blankComments(file.readText())
            UI_STRING.findAll(text).forEach match@{ match ->
                val literal = match.groups["literal"]!!.value
                // A bare interpolation carries no words of its own.
                if (literal.startsWith("$") && !literal.contains(' ')) return@match
                // The masthead and the two feature names stay in English.
                if (literal.trim() in BRAND) return@match
                val line = text.substring(0, match.range.first).count { it == '\n' } + 1
                found.getOrPut(rel) { mutableListOf() }.add(line to literal)
            }
        }

        val allowed = allowlist.get().asFile.readLines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .associate { it.substringBeforeLast(' ').trim() to it.substringAfterLast(' ').trim().toInt() }

        val problems = mutableListOf<String>()
        for ((path, hits) in found) {
            val budget = allowed[path] ?: 0
            if (hits.size > budget) {
                problems += buildString {
                    appendLine("$path: ${hits.size} hardcoded string(s), allowed $budget")
                    hits.takeLast(hits.size - budget).forEach { (line, literal) ->
                        appendLine("    $path:$line  \"${literal.take(60)}\"")
                    }
                }
            }
        }

        // The ratchet's other half: an entry whose file has improved must be
        // tightened, or the slack it no longer needs quietly lets a regression
        // back in later.
        val stale = allowed.filter { (path, budget) -> (found[path]?.size ?: 0) < budget }
        if (stale.isNotEmpty()) {
            problems += buildString {
                appendLine("These files now have fewer hardcoded strings than the allowlist permits.")
                appendLine("Lower the number (or delete the line, if it is now zero):")
                stale.forEach { (path, budget) -> appendLine("    $path  $budget -> ${found[path]?.size ?: 0}") }
            }
        }

        if (problems.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Interface copy must live in core/design/src/main/res/values/strings.xml,")
                    appendLine("reached with stringResource(DesignR.string.…), so it can be translated.")
                    appendLine("See that file's header for how a locale is added.")
                    appendLine()
                    problems.forEach { appendLine(it) }
                },
            )
        }

        logger.lifecycle(
            "verifyI18n: ${found.values.sumOf { it.size }} string(s) still to move, " +
                "across ${found.size} file(s).",
        )
    }
}

// :core:model and :core:data are deliberately absent — their strings are feed
// data, publisher names and lexicon terms. Those are not interface copy, and
// translating a publisher's own masthead would be a bug, not a feature.
val i18nScannedRoots = listOf(
    "app/src/main",
    "core/design/src/main",
    "feature/reader/src/main",
    "feature/river/src/main",
    "feature/sources/src/main",
    "feature/lens/src/main",
)

tasks.register<VerifyI18nTask>("verifyI18n") {
    group = "verification"
    description = "Fails on interface copy hardcoded in Kotlin instead of strings.xml."
    sources.from(i18nScannedRoots.map { rootProject.fileTree(it) { include("**/*.kt") } })
    allowlist.set(rootProject.layout.projectDirectory.file("gradle/i18n/i18n-allowlist.txt"))
    repoRoot.set(rootProject.projectDir.absolutePath)
}
