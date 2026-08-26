package com.homedesign.android.domain.geom

import org.junit.Assert.assertEquals
import org.junit.Test

class OrthoLockTest {
    @Test
    fun constrainPicksDominantAxis() {
        val start = vec(100.0, 100.0)
        val h = OrthoLock.constrain(start, vec(300.0, 120.0))
        assertEquals(300.0, h.x, 1e-9)
        assertEquals(100.0, h.y, 1e-9)

        val v = OrthoLock.constrain(start, vec(115.0, 400.0))
        assertEquals(100.0, v.x, 1e-9)
        assertEquals(400.0, v.y, 1e-9)
    }

    @Test
    fun constrainToOctantPreservesLengthOnDiagonal() {
        val start = vec(0.0, 0.0)
        val raw = vec(100.0, 20.0)
        val out = OrthoLock.constrainToOctant(start, raw)
        assertEquals(length(raw), length(sub(out, start)), 1e-9)
        // Near-horizontal should snap to +X.
        assertEquals(length(raw), out.x, 1e-6)
        assertEquals(0.0, out.y, 1e-6)
    }

    @Test
    fun constrainToOctantSnapsFortyFive() {
        val start = vec(10.0, 10.0)
        val raw = vec(10.0 + 100.0, 10.0 + 90.0)
        val out = OrthoLock.constrainToOctant(start, raw)
        val d = sub(out, start)
        assertEquals(d.x, d.y, 1e-6)
        assertEquals(length(sub(raw, start)), length(d), 1e-6)
    }

    @Test
    fun constrainToOctantZeroLength() {
        val start = vec(5.0, 5.0)
        val out = OrthoLock.constrainToOctant(start, start)
        assertEquals(5.0, out.x, 1e-9)
        assertEquals(5.0, out.y, 1e-9)
    }
}
