package com.homedesign.android.domain.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SH3DReaderTest {

    @Test
    fun readsTestSmallFixture() {
        val bytes = javaClass.classLoader!!
            .getResourceAsStream("test_small.sh3d")!!
            .use { it.readBytes() }
        val home = SH3DReader.read(bytes)
        assertEquals("test_small.sh3d", home.name)
        assertEquals(243.84, home.wallHeight, 0.001)
        assertEquals(5, home.walls.size)
        assertEquals(2, home.rooms.size)
        assertEquals(2, home.furniture.size)
        assertEquals(2, home.doorsAndWindows.size)
        assertTrue(home.levels.isNotEmpty())
        assertTrue(home.walls.all { it.level != null })
        assertTrue(home.doorsAndWindows.first().sashes.isNotEmpty())
        // Embedded sofa model='1' resolves to sniffed .obj on disk.
        val sofa = home.furniture.first { it.name == "Sofa" }
        assertEquals("1", sofa.modelRef)
        assertTrue(sofa.modelURL != null && sofa.modelURL!!.endsWith(".obj"))
    }

    @Test
    fun parseHomeXmlWallsOnly() {
        val xml = """
            <?xml version='1.0'?>
            <home version='7400' name='tiny' wallHeight='250'>
              <wall id='w1' xStart='0' yStart='0' xEnd='100' yEnd='0' thickness='10' height='250'/>
              <wall id='w2' xStart='100' yStart='0' xEnd='100' yEnd='80' thickness='10'/>
              <room id='r1' name='Hall'>
                <point x='0' y='0'/>
                <point x='100' y='0'/>
                <point x='100' y='80'/>
                <point x='0' y='80'/>
              </room>
              <pieceOfFurniture id='p1' name='Chair' x='40' y='40' width='50' depth='50' height='80'/>
              <pieceOfFurniture id='s1' name='Staircase' x='20' y='20' width='90' depth='240' height='250'
                staircaseCutOutShape='M0,0 1,0 1,1 0,1 z'/>
            </home>
        """.trimIndent().toByteArray()
        val home = SH3DReader.parseHomeXml(xml)
        assertEquals("tiny", home.name)
        assertEquals(2, home.walls.size)
        assertEquals(1, home.rooms.size)
        assertEquals(4, home.rooms[0].points.size)
        assertEquals(2, home.furniture.size)
        assertEquals(HomeFactorySynthLevel, home.walls[0].level)
        assertNull(home.furniture.first { it.id == "p1" }.staircaseCutOut)
        assertEquals(true, home.furniture.first { it.id == "s1" }.staircaseCutOut)
    }

    companion object {
        private val HomeFactorySynthLevel = com.homedesign.android.domain.model.HomeFactory.SYNTH_LEVEL_ID
    }
}
