package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartersTest {

    @Test fun everyVerifiedFeedHasResolvableKindAndUrl() {
        for (s in Starters.verifiedFeeds) {
            assertNotNull("kind resolves: ${s.id}", s.sourceKind)
            assertTrue("has url: ${s.id}", !s.url.isNullOrBlank())
            assertEquals("verified stamp: ${s.id}", "2026-07-07", s.verifiedAt)
            assertNotNull("addable: ${s.id}", s.toSourceOrNull(addedAt = 1L))
        }
    }

    @Test fun starterIdsAreUnique() {
        val all = Starters.seed.services.map { it.id }
        assertEquals(all.size, all.toSet().size)
    }

    @Test fun regionallyBalancedGlobalAndIndia() {
        val byRegion = Starters.feedsByRegion()
        assertTrue("has global", (byRegion["global"]?.size ?: 0) >= 5)
        assertTrue("has india", (byRegion["india"]?.size ?: 0) >= 5)
    }

    @Test fun coversAtLeastThreeSourceKindsEndToEnd() {
        // Gate: connect >=3 source kinds. The seed exposes rss + the builder kinds.
        val kinds = Starters.seed.services.mapNotNull { it.sourceKind }.toSet()
        assertTrue(kinds.contains(SourceKind.RSS))
        assertTrue(kinds.contains(SourceKind.GOOGLE_NEWS))
        assertTrue(kinds.contains(SourceKind.GDELT))
        assertTrue(kinds.contains(SourceKind.MASTODON))
        assertTrue("at least 4 kinds seeded", kinds.size >= 4)
    }

    @Test fun keyedProvidersAreHonestlyLabeled() {
        val guardian = Starters.builders.first { it.id == "guardian-open-platform" }
        assertTrue(guardian.requiresKey)
        assertNotNull(guardian.keySignupUrl)
        val gnews = Starters.tierBReference.first { it.id == "gnews" }
        assertTrue(gnews.requiresKey)
        assertEquals(SourceKind.API, gnews.sourceKind)
    }
}
