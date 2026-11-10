package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Oracle for the XML parser's XXE hardening: a feed carrying a DOCTYPE or an
 * external entity must be refused, and nothing the entity points at may reach
 * the parsed output. Any replacement XML parser has to keep passing this.
 */
class FeedParserXxeTest {

    private val secret = "XXE-SECRET-MARKER-7f3a"

    private fun secretFile(): File =
        File.createTempFile("xxe-secret", ".txt").apply {
            writeText(secret)
            deleteOnExit()
        }

    @Test
    fun externalEntityPayloadYieldsNoItemsAndNoFileContent() {
        val target = secretFile().toURI()
        val feed = """
            <?xml version="1.0"?>
            <!DOCTYPE rss [ <!ENTITY xxe SYSTEM "$target"> ]>
            <rss version="2.0"><channel><title>&xxe;</title>
              <item><title>&xxe;</title><link>https://ex.com/a</link>
                <description>&xxe;</description></item>
            </channel></rss>
        """.trimIndent()

        val parsed = FeedParser.parse(feed, "application/rss+xml")

        assertTrue(parsed.items.isEmpty())
        assertFalse(parsed.title.orEmpty().contains(secret))
    }

    @Test
    fun parameterEntityDoctypeIsRefused() {
        val target = secretFile().toURI()
        val feed = """
            <?xml version="1.0"?>
            <!DOCTYPE feed [ <!ENTITY % ext SYSTEM "$target"> %ext; ]>
            <feed xmlns="http://www.w3.org/2005/Atom"><title>t</title>
              <entry><title>e</title><link rel="alternate" href="https://ex.com/c"/></entry>
            </feed>
        """.trimIndent()

        val parsed = FeedParser.parse(feed, "application/atom+xml")

        assertEquals(0, parsed.items.size)
    }

    @Test
    fun internalEntityDoctypeIsRefused() {
        val feed = """
            <?xml version="1.0"?>
            <!DOCTYPE rss [ <!ENTITY lol "lol"> ]>
            <rss version="2.0"><channel><title>t</title>
              <item><title>&lol;</title><link>https://ex.com/a</link></item>
            </channel></rss>
        """.trimIndent()

        assertEquals(0, FeedParser.parse(feed, "application/rss+xml").items.size)
    }
}
