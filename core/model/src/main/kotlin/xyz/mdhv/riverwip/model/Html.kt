package xyz.mdhv.riverwip.model

/**
 * Tiny HTML→text helper for feed titles/summaries and Mastodon content. Strips
 * tags and decodes the common entities. Not a full HTML parser (the reader's
 * full-text extraction uses jsoup in `:core:data`); this is just enough to turn a
 * feed's escaped/marked-up snippet into clean display text. Pure.
 */
object Html {
    private val TAG = Regex("<[^>]+>")
    private val WS = Regex("\\s+")
    private val IMG_SRC = Regex("<img\\b[^>]*\\bsrc\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

    fun strip(s: String): String =
        unescape(TAG.replace(s, " ")).let { WS.replace(it, " ") }.trim()

    /**
     * One recognized trailing fragment — the syndication/ad plumbing a feed
     * appends after the real writing stops, not writing itself. Every
     * pattern is anchored to the END of the string with `$`, so a real
     * sentence is never truncated mid-way: only a whole, recognizable
     * fragment with nothing real after it is ever removed. Reported
     * (screenshot: "links coming through or some sort of ads... the article
     * itself isn't fully clean") because `strip()` above only removes HTML
     * *tags* — it has no idea "Continue reading →" or a WordPress
     * syndication footer is filler rather than the writer's own last
     * sentence, so both survived as ordinary plain text.
     *
     * Mirrored in `web/js/articleBoilerplate.js` for the web reader's own
     * pipeline (a separate implementation — see that file's header) and
     * reused per-paragraph by `:core:data`'s `ArticleExtractor`; keep all
     * three in sync.
     */
    private val TRAILING_BOILERPLATE = listOf(
        // WordPress/Jetpack's byte-for-byte auto-appended syndication footer.
        Regex("""(?i)\bThe post .+? appeared first on .+?\.$"""),
        // A "keep reading" link's own label, alone or with a trailing arrow/ellipsis --
        // never this much text unless it's the whole label, so "read more about the
        // case below" (real prose) can't match: there's no room for it before the `$`.
        Regex("""(?i)\b(continue reading|read more|read (the )?full (article|story)|keep reading|full story)\s*[:\-–—»›→]*\s*(\.{2,3}|…)?$"""),
        // An ad-network label with nothing else around it.
        Regex("""(?i)\b(advertisement|sponsored(\s+content)?)$"""),
        // WordPress excerpt truncation markers ("...the rest of the story [&#8230;]").
        Regex("""\[(…|\.\.\.)\]$"""),
        // A bare tracking/redirect link with no surrounding label at all -- in a
        // plain-text reader this is never clickable, so it is pure clutter either way.
        Regex("""\bhttps?://\S+$"""),
    )

    /**
     * Strips recognized trailing boilerplate fragments (see
     * [TRAILING_BOILERPLATE]) one at a time until none remain — a feed can
     * chain more than one ("...real ending. [&#8230;] Continue reading →").
     * If the whole string turns out to be nothing but such fragments, the
     * result is blank, which callers already treat as "no summary" rather
     * than displaying pure junk.
     */
    fun stripTrailingBoilerplate(s: String): String {
        var out = s.trim()
        var changed = true
        while (changed && out.isNotEmpty()) {
            changed = false
            for (pattern in TRAILING_BOILERPLATE) {
                val match = pattern.find(out) ?: continue
                if (match.range.last != out.lastIndex) continue
                // NOT '.': a period right before the cut is the real sentence's
                // own terminal punctuation ("...budget. The post ... appeared
                // first on ..."), never a separator glued onto the boilerplate --
                // only whitespace and connector marks (dash/colon/comma) are.
                out = out.substring(0, match.range.first).trim(' ', '-', '–', '—', ':', ',', '\t')
                changed = true
                break
            }
        }
        return out
    }

    /**
     * The first `<img src="...">` found in a raw (unstripped) HTML snippet, if
     * any — the last-resort image source for a feed item whose entry carries
     * no structured `<enclosure>`/Media RSS/Atom image link, only an `<img>`
     * buried in its description/content HTML.
     */
    fun firstImgSrc(s: String): String? = IMG_SRC.find(s)?.groupValues?.get(1)?.let(::unescape)?.trim()?.ifBlank { null }

    fun unescape(s: String): String {
        if ('&' !in s) return s
        var r = s
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&hellip;", "…")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&rsquo;", "’")
            .replace("&lsquo;", "‘")
            .replace("&ldquo;", "“")
            .replace("&rdquo;", "”")
        // Numeric entities &#NNN; and &#xHH;
        r = Regex("&#x([0-9a-fA-F]+);").replace(r) { m ->
            m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
        }
        r = Regex("&#([0-9]+);").replace(r) { m ->
            m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
        }
        return r
    }
}
