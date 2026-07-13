package xyz.mdhv.riverwip.data.extract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleExtractorTest {

    private val cleanArticle = """
        <html><head><title>Government unveils new climate policy</title></head>
        <body>
          <nav><a href="/">Home</a><a href="/world">World</a><a href="/sport">Sport</a></nav>
          <header><a href="/login">Log in</a></header>
          <article>
            <h1>Government unveils new climate policy</h1>
            <div class="byline">By A. Reporter</div>
            <p>The government announced a sweeping new climate policy on Wednesday, aiming to cut emissions by forty percent before the end of the decade through a mix of subsidies and regulation.</p>
            <p>Officials said the plan would be phased in over five years, with the first measures targeting the energy sector and heavy industry, which together account for most of the country's emissions.</p>
            <p>Opposition lawmakers criticized the timeline as too slow, while environmental groups welcomed the announcement as a long overdue step in the right direction for climate policy.</p>
          </article>
          <aside class="sidebar"><h3>Related</h3><p><a href="/a">Story A</a></p><p><a href="/b">Story B</a></p></aside>
          <footer><a href="/privacy">Privacy</a><a href="/terms">Terms</a><p>Copyright 2026</p></footer>
        </body></html>
    """.trimIndent()

    @Test fun extractsMainArticleBodyOnly() {
        val result = ArticleExtractor.extract(cleanArticle, "https://ex.com/story")
        assertEquals("Government unveils new climate policy", result.title)
        assertEquals(3, result.paragraphs.size)
        assertTrue(result.paragraphs[0].contains("sweeping new climate policy"))
        // Nav/footer/sidebar links never leak into the body.
        assertTrue(result.paragraphs.none { it.contains("Copyright") || it.contains("Story A") })
    }

    @Test fun extractsByline() {
        val result = ArticleExtractor.extract(cleanArticle, "https://ex.com/story")
        assertEquals("By A. Reporter", result.byline)
    }

    @Test fun rejectsLinkHeavyParagraphs() {
        val linkHeavy = """
            <html><body><article>
              <h1>Test</h1>
              <p>See also: <a href="/a">Story A</a>, <a href="/b">Story B</a>, <a href="/c">Story C</a>, <a href="/d">Story D</a></p>
              <p>This is a genuine paragraph of real article content with enough substantial text to pass the minimum length threshold for extraction.</p>
            </article></body></html>
        """.trimIndent()
        val result = ArticleExtractor.extract(linkHeavy)
        assertEquals(1, result.paragraphs.size)
        assertTrue(result.paragraphs[0].contains("genuine paragraph"))
    }

    @Test fun emptyOrGarbageHtmlYieldsNoParagraphs() {
        assertTrue(ArticleExtractor.extract("").paragraphs.isEmpty())
        assertTrue(ArticleExtractor.extract("<html><body><p>short</p></body></html>").paragraphs.isEmpty())
        assertTrue(!ArticleExtractor.extract("not even html").isUsable)
    }

    @Test fun picksTheContainerWithMostGoodParagraphsWhenScattered() {
        // A page with one substantial article block plus a stray unrelated
        // substantial-looking paragraph outside it (e.g. a "featured quote" widget) —
        // the article's own container should still win since it holds more.
        val html = """
            <html><body>
              <div class="widget"><p>This is a long promotional pull-quote that is not really part of the article body content at all really.</p></div>
              <article>
                <p>First real paragraph of the actual article content, long enough to count as substantial for extraction purposes here.</p>
                <p>Second real paragraph continuing the actual article content, also long enough to count as substantial for extraction here.</p>
                <p>Third real paragraph wrapping up the actual article content, once again long enough to count as substantial for extraction.</p>
              </article>
            </body></html>
        """.trimIndent()
        val result = ArticleExtractor.extract(html)
        assertEquals(3, result.paragraphs.size)
        assertTrue(result.paragraphs.all { it.contains("actual article content") })
    }

    @Test fun handlesEachParagraphWrappedInItsOwnContainer() {
        // Many real templates wrap *every* paragraph in a one-off `<div>` (a
        // per-block component) instead of nesting them as siblings directly
        // inside the article. A direct-parent-only heuristic would isolate
        // each paragraph into its own single-item "container" and only one
        // paragraph would survive extraction — this is the real-world bug
        // behind sources falling back to "open it in the browser" too often.
        val html = """
            <html><body>
              <article>
                <h1>Wrapped paragraphs</h1>
                <div class="block"><p>First real paragraph of the actual article content, long enough to count as substantial for extraction purposes here.</p></div>
                <div class="block"><p>Second real paragraph continuing the actual article content, also long enough to count as substantial for extraction here.</p></div>
                <div class="block"><p>Third real paragraph wrapping up the actual article content, once again long enough to count as substantial for extraction.</p></div>
              </article>
            </body></html>
        """.trimIndent()
        val result = ArticleExtractor.extract(html)
        assertEquals(3, result.paragraphs.size)
        assertTrue(result.paragraphs.all { it.contains("actual article content") })
    }

    @Test fun fallsBackToBareDivsWhenNoSemanticMarkupExists() {
        // Some older/legacy templates markup body copy with plain `<div>`s
        // and no `<p>`, `<article>`, `<main>`, or schema.org markup at all.
        val html = """
            <html><body>
              <div class="page">
                <div class="headline">Some legacy template</div>
                <div class="body-copy">This is the first paragraph of body copy rendered as a bare div instead of a semantic paragraph tag, which some older templates still do.</div>
                <div class="body-copy">This is the second paragraph of body copy, also rendered as a bare div, continuing on with enough substantial text to pass the length filter.</div>
              </div>
            </body></html>
        """.trimIndent()
        val result = ArticleExtractor.extract(html)
        assertEquals(2, result.paragraphs.size)
        assertTrue(result.paragraphs.all { it.contains("body copy") })
    }

    @Test fun listItemsAreKeptWithABulletMarker() {
        // List content (how-tos, explainers) was previously dropped entirely
        // since only `<p>` was ever considered a paragraph.
        val html = """
            <html><body><article>
              <h1>A how-to</h1>
              <p>Here is an introduction paragraph long enough to pass the minimum length threshold for extraction purposes.</p>
              <ul>
                <li>First step in the process, described with enough substantial detail to pass the length filter here.</li>
                <li>Second step in the process, again with enough substantial detail to pass the length filter here too.</li>
              </ul>
            </article></body></html>
        """.trimIndent()
        val result = ArticleExtractor.extract(html)
        assertTrue(result.paragraphs.any { it.startsWith("•") && it.contains("First step") })
        assertTrue(result.paragraphs.any { it.startsWith("•") && it.contains("Second step") })
    }
}
