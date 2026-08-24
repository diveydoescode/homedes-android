package com.homedesign.android.domain

import com.homedesign.android.domain.geom.WallGeometry
import com.homedesign.android.domain.model.UnitFormat
import com.homedesign.android.domain.model.UnitSystem
import com.homedesign.android.domain.model.Wall
import com.homedesign.android.domain.settings.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers unit formatting and wall outline geometry twice each (A/B pairs)
 * so regressions in mm/cm/ft display or wall look helpers fail loudly.
 */
class UnitSystemAndWallTest {

    // —— UnitFormat.length covered twice per system ——

    @Test
    fun length_millimetre_caseA() {
        assertEquals("500 mm", UnitFormat.length(50.0, UnitSystem.Millimetre))
    }

    @Test
    fun length_millimetre_caseB() {
        assertEquals("100 mm", UnitFormat.length(10.0, UnitSystem.Millimetre))
        assertEquals("200 mm", UnitFormat.length(20.0, UnitSystem.Millimetre))
    }

    @Test
    fun length_centimetre_caseA() {
        assertEquals("50 cm", UnitFormat.length(50.0, UnitSystem.Metric))
    }

    @Test
    fun length_centimetre_caseB() {
        assertEquals("10 cm", UnitFormat.length(10.0, UnitSystem.Metric))
        assertTrue(UnitFormat.length(150.0, UnitSystem.Metric).endsWith("m"))
    }

    @Test
    fun length_feet_caseA() {
        // 304.8 cm = 10 ft
        assertEquals("10′", UnitFormat.length(304.8, UnitSystem.Imperial))
    }

    @Test
    fun length_feet_caseB() {
        val tenCm = UnitFormat.length(10.0, UnitSystem.Imperial)
        assertTrue(tenCm.contains("″") || tenCm.contains("′"))
        val twentyCm = UnitFormat.length(20.0, UnitSystem.Imperial)
        assertNotEquals(tenCm, twentyCm)
    }

    // —— Area covered twice (metric + imperial) ——

    @Test
    fun area_metric_caseA() {
        assertTrue(UnitFormat.area(21.6, UnitSystem.Metric).contains("21.60"))
        assertTrue(UnitFormat.area(21.6, UnitSystem.Millimetre).endsWith("m²"))
    }

    @Test
    fun area_imperial_caseB() {
        val ft2 = UnitFormat.area(21.6, UnitSystem.Imperial)
        assertTrue(ft2.contains("ft²"))
        assertTrue(ft2.startsWith("232") || ft2.startsWith("233"))
    }

    // —— Suffix / round-trip covered twice ——

    @Test
    fun suffix_allSystems_caseA() {
        assertEquals("mm", UnitFormat.suffix(UnitSystem.Millimetre))
        assertEquals("cm", UnitFormat.suffix(UnitSystem.Metric))
        assertEquals("ft", UnitFormat.suffix(UnitSystem.Imperial))
    }

    @Test
    fun fromToUnit_roundTrip_caseB() {
        val cm = 125.0
        for (system in UnitSystem.entries) {
            val display = UnitFormat.toUnit(cm, system)
            val back = UnitFormat.fromUnit(display, system)
            assertEquals("round-trip $system", cm, back, 0.51)
        }
    }

    // —— Settings helper ——

    @Test
    fun userSettings_useMetric_derivesFromUnitSystem_twice() {
        assertTrue(UserSettings(unitSystem = UnitSystem.Millimetre).useMetric)
        assertTrue(UserSettings(unitSystem = UnitSystem.Metric).useMetric)
        assertFalse(UserSettings(unitSystem = UnitSystem.Imperial).useMetric)
    }

    // —— Wall outline (look) covered twice ——

    @Test
    fun wallUnjoinedOutline_hasFourCorners_caseA() {
        val wall = sampleWall(0.0, 0.0, 100.0, 0.0, thickness = 10.0)
        val pts = WallGeometry.unjoinedOutline(wall)
        assertEquals(4, pts.size)
        // Thickness should push Y by ±5 cm for a horizontal wall (left = −, right = +)
        assertEquals(-5.0, pts[0].y, 1e-6)
        assertEquals(5.0, pts[3].y, 1e-6)
    }

    @Test
    fun wallUnjoinedOutline_respectsThickness_caseB() {
        val thin = WallGeometry.unjoinedOutline(sampleWall(0.0, 0.0, 200.0, 0.0, 10.0))
        val thick = WallGeometry.unjoinedOutline(sampleWall(0.0, 0.0, 200.0, 0.0, 20.0))
        val thinSpan = kotlin.math.abs(thin[0].y - thin[3].y)
        val thickSpan = kotlin.math.abs(thick[0].y - thick[3].y)
        assertEquals(10.0, thinSpan, 1e-6)
        assertEquals(20.0, thickSpan, 1e-6)
    }

    @Test
    fun wallMiteredPoints_withoutNeighbours_matchesUnjoined_twice() {
        val a = sampleWall(0.0, 0.0, 80.0, 0.0, 10.0)
        val b = sampleWall(0.0, 0.0, 0.0, 120.0, 20.0)
        val map = emptyMap<String, Wall>()
        assertEquals(WallGeometry.unjoinedOutline(a), WallGeometry.miteredPoints(a, map))
        assertEquals(WallGeometry.unjoinedOutline(b), WallGeometry.miteredPoints(b, map))
    }

    private fun sampleWall(
        sx: Double,
        sy: Double,
        ex: Double,
        ey: Double,
        thickness: Double,
    ) = Wall(
        id = "w",
        startX = sx,
        startY = sy,
        endX = ex,
        endY = ey,
        thickness = thickness,
        height = 250.0,
    )
}
