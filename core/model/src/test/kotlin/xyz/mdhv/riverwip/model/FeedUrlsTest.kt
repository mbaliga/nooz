package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedUrlsTest {

    @Test fun googleNewsLocaleTriple() {
        val loc = FeedUrls.GNLocale.of("en", "IN")
        assertEquals("en-IN", loc.hl)
        assertEquals("IN", loc.gl)
        assertEquals("IN:en", loc.ceid)
    }

    @Test fun googleNewsTopUsesLocale() {
        val url = FeedUrls.googleNewsTop(FeedUrls.GNLocale.US_EN)
        assertEquals("https://news.google.com/rss?hl=en-US&gl=US&ceid=US%3Aen", url)
    }

    @Test fun googleNewsSearchEncodesQuery() {
        val url = FeedUrls.googleNewsSearch("climate policy", FeedUrls.GNLocale.INDIA_EN)
        assertTrue(url.startsWith("https://news.google.com/rss/search?q=climate%20policy&"))
        assertTrue(url.contains("ceid=IN%3Aen"))
    }

    @Test fun googleNewsSectionUsesTopicName() {
        val url = FeedUrls.googleNewsSection(FeedUrls.GNTopic.TECHNOLOGY, FeedUrls.GNLocale.UK_EN)
        assertTrue(url.contains("/section/topic/TECHNOLOGY?"))
        assertTrue(url.contains("gl=GB"))
    }

    @Test fun gdeltClampsAndFormats() {
        val url = FeedUrls.gdeltDoc(FeedUrls.GdeltQuery(query = "flood india", maxRecords = 999, timespanHours = 48))
        assertTrue(url.contains("query=flood%20india"))
        assertTrue(url.contains("mode=artlist"))
        assertTrue(url.contains("format=json"))
        assertTrue(url.contains("maxrecords=250")) // clamped from 999
        assertTrue(url.contains("timespan=48h"))
        assertTrue(url.contains("sort=datedesc"))
    }

    @Test fun mastodonPublicAndTag() {
        val pub = FeedUrls.mastodonPublic("https://mastodon.social/", limit = 100, localOnly = true)
        assertEquals("https://mastodon.social/api/v1/timelines/public?limit=40&local=true", pub)
        val tag = FeedUrls.mastodonTag("mastodon.social", "#News", limit = 20)
        assertEquals("https://mastodon.social/api/v1/timelines/tag/News?limit=20", tag)
    }
}
