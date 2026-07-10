package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobeModelTest {

    @Test fun wrapLonNormalises() {
        assertEquals(0.0, GlobeModel.wrapLon(360.0), 1e-9)
        assertEquals(-170.0, GlobeModel.wrapLon(190.0), 1e-9)
        assertEquals(170.0, GlobeModel.wrapLon(-190.0), 1e-9)
    }

    @Test fun landHitsAndMisses() {
        assertTrue(GlobeModel.isLand(10.0, 45.0))   // Europe
        assertTrue(GlobeModel.isLand(78.0, 20.0))   // India
        assertFalse(GlobeModel.isLand(-40.0, 20.0)) // mid-Atlantic
        assertFalse(GlobeModel.isLand(170.0, -50.0)) // southern ocean
    }

    @Test fun dotGridIsNonTrivialAndStable() {
        val n = GlobeModel.dots.size
        assertTrue("dot grid should be substantial, was $n", n > 200)
        assertEquals(n, GlobeModel.dots.size) // lazy value stable
    }

    @Test fun projectionCentreAndFarSide() {
        // View centred on (0,0): that point projects to the origin with distance 0.
        val centre = GlobeModel.project(0.0, 0.0, 0.0, 0.0)!!
        assertEquals(0.0, centre.x, 1e-9)
        assertEquals(0.0, centre.y, 1e-9)
        assertEquals(0.0, centre.distance, 1e-9)
        // The antipode is invisible.
        assertNull(GlobeModel.project(180.0, 0.0, 0.0, 0.0))
    }

    @Test fun projectionYawBringsPointsIntoView() {
        // Point at lon=90 is on the limb for yaw 0, centred for yaw -90.
        val centred = GlobeModel.project(90.0, 0.0, -90.0, 0.0)!!
        assertEquals(0.0, centred.distance, 1e-9)
        assertEquals(90.0, GlobeModel.centerLongitude(-90.0), 1e-9)
    }

    @Test fun bandMembershipAndGlobalThreshold() {
        assertTrue(GlobeModel.inBand(10.0, 0.0, 16.0))
        assertFalse(GlobeModel.inBand(30.0, 0.0, 16.0))
        assertTrue(GlobeModel.inBand(179.0, 0.0, GlobeModel.GLOBAL_BAND_THRESHOLD))
    }

    @Test fun regionForLongitudeCoversTheCircle() {
        assertEquals(Region.AMERICAS, Region.forLongitude(-100.0))
        assertEquals(Region.EUROPE_AFRICA, Region.forLongitude(10.0))
        assertEquals(Region.MIDEAST_CASIA, Region.forLongitude(50.0))
        assertEquals(Region.SOUTH_ASIA, Region.forLongitude(78.0))
        assertEquals(Region.EAST_SE_ASIA, Region.forLongitude(120.0))
        assertEquals(Region.AUSTRALIA_PACIFIC, Region.forLongitude(150.0))
        assertEquals(Region.AUSTRALIA_PACIFIC, Region.forLongitude(-175.0)) // antimeridian wrap
    }

    @Test fun sourceTagMapping() {
        assertEquals(Region.GLOBAL, Region.forSourceTag("global"))
        assertEquals(Region.GLOBAL, Region.forSourceTag(null))
        assertEquals(Region.SOUTH_ASIA, Region.forSourceTag("india"))
        assertEquals(Region.EAST_SE_ASIA, Region.forSourceTag("east-se-asia"))
    }

    @Test fun filterRules() {
        val global = ReaderFilter()
        assertTrue(global.includesSource("india"))
        assertTrue(global.matchesTopic(Topic.SPORT))
        val southAsiaPolitics = ReaderFilter(Region.SOUTH_ASIA, setOf(Topic.POLITICS.key))
        assertTrue(southAsiaPolitics.includesSource("india"))
        assertTrue(southAsiaPolitics.includesSource("global")) // world feeds cover every sector
        assertFalse(southAsiaPolitics.matchesTopic(Topic.SPORT))
        assertTrue(southAsiaPolitics.matchesTopic(Topic.POLITICS))
        assertEquals("Global | All", global.summary())
    }

    @Test fun legacyThemeKeysMapToPaper() {
        assertEquals(ThemeMode.PAPER, ThemeMode.fromKey("light"))
        assertEquals(ThemeMode.PAPER, ThemeMode.fromKey("system"))
        assertEquals(ThemeMode.WHITE, ThemeMode.fromKey("white"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromKey("dark"))
        assertEquals(ThemeMode.PAPER, ThemeMode.WHITE.next().next().next().next()) // cycle of 3
        assertEquals(ThemeMode.DARK, ThemeMode.PAPER.next())
    }
}
