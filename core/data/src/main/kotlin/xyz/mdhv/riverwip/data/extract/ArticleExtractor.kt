package xyz.mdhv.riverwip.data.extract

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Lightweight Readability-style full-text extraction (brief §P3: used when feeds
 * truncate). Pure jsoup DOM heuristics — no network, no Android dependency, so
 * it is unit-tested against real article HTML on the JVM.
 *
 * Algorithm: strip boilerplate tags, score `<p>` elements as "good" (substantial
 * text, low link-density — i.e. not a nav/share-bar full of links), then pick the
 * parent container holding the most good paragraphs as the article body.
 */
object ArticleExtractor {

    data class Extracted(
        val title: String?,
        val byline: String?,
        val paragraphs: List<String>,
    ) {
        val isUsable: Boolean get() = paragraphs.isNotEmpty()
    }

    private const val MIN_PARAGRAPH_LEN = 40
    private const val MAX_LINK_DENSITY = 0.3

    fun extract(html: String, baseUri: String = ""): Extracted {
        val doc = try {
            Jsoup.parse(html, baseUri)
        } catch (_: Exception) {
            return Extracted(null, null, emptyList())
        }
        doc.select("script, style, noscript, iframe, form, svg, nav, header, footer, aside").remove()

        val title = doc.selectFirst("h1")?.text()?.ifBlank { null } ?: doc.title().ifBlank { null }
        val byline = doc.selectFirst("[rel=author], .byline, .author, meta[name=author]")
            ?.let { if (it.tagName() == "meta") it.attr("content") else it.text() }
            ?.ifBlank { null }

        val goodParagraphs = doc.select("p").filter { isGoodParagraph(it) }
        if (goodParagraphs.isEmpty()) return Extracted(title, byline, emptyList())

        // The container (direct parent) holding the most good paragraphs is the
        // article body — boilerplate paragraphs (nav links, footers) tend to be
        // isolated or share a container with few other substantial paragraphs.
        val counts = HashMap<Element, Int>()
        for (p in goodParagraphs) {
            val parent = p.parent() ?: continue
            counts[parent] = (counts[parent] ?: 0) + 1
        }
        val bestContainer = counts.maxByOrNull { it.value }?.key

        val finalParagraphs = (if (bestContainer != null) goodParagraphs.filter { it.parent() == bestContainer } else goodParagraphs)
            .map { it.text().trim() }
            .filter { it.isNotBlank() }

        return Extracted(title, byline, finalParagraphs)
    }

    private fun isGoodParagraph(p: Element): Boolean {
        val text = p.text().trim()
        if (text.length < MIN_PARAGRAPH_LEN) return false
        return linkDensity(p) < MAX_LINK_DENSITY
    }

    private fun linkDensity(el: Element): Double {
        val textLen = el.text().length
        if (textLen == 0) return 1.0
        val linkTextLen = el.select("a").sumOf { it.text().length }
        return linkTextLen.toDouble() / textLen
    }
}
