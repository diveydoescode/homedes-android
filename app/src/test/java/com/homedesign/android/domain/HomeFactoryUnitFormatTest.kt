package com.homedesign.android.domain

import com.homedesign.android.domain.io.HomedesignZip
import com.homedesign.android.domain.model.HomeFactory
import com.homedesign.android.domain.model.UnitFormat
import com.homedesign.android.domain.model.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFactoryUnitFormatTest {
    @Test
    fun emptyHome_hasSynthLevelAndNoGeometry() {
        val home = HomeFactory.emptyHome("Demo")
        assertEquals("Demo", home.name)
        assertEquals(1, home.levels.size)
        assertEquals(HomeFactory.SYNTH_LEVEL_ID, home.selectedLevelID)
        assertTrue(home.walls.isEmpty())
        assertTrue(home.rooms.isEmpty())
    }

    @Test
    fun unitFormat_metricLength() {
        assertEquals("50 cm", UnitFormat.length(50.0, UnitSystem.Metric))
        val meters = UnitFormat.length(150.0, UnitSystem.Metric)
        assertTrue(meters.contains("1.5") && meters.endsWith("m"))
    }

    @Test
    fun homedesignRoundTrip_preservesName() {
        val home = HomeFactory.emptyHome("Round trip")
        val bytes = HomedesignZip.encode(home)
        val decoded = HomedesignZip.decode(bytes)
        assertEquals("Round trip", decoded.name)
        assertEquals(home.levels.size, decoded.levels.size)
    }
}
