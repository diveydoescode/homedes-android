package com.homedesign.android.domain.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class Sh3dMeshExtractionTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun extractAll_sniffsFlatObjAndPng() {
        val bytes = fixtureBytes("test_small.sh3d")
        val cache = tmp.newFolder("meshes")
        val urls = SH3DMeshExtraction.extractAll(bytes, cache)
        assertFalse(urls.containsKey("Home.xml"))
        assertFalse(urls.containsKey("Home"))
        assertFalse(urls.containsKey("ContentDigests"))

        val sofa = urls["1"]
        assertNotNull(sofa)
        assertTrue(sofa!!.endsWith("1.obj"))
        assertTrue(File(sofa).isFile)

        val icon = urls["0"]
        assertNotNull(icon)
        assertTrue(icon!!.endsWith("0.png"))
    }

    @Test
    fun reader_attachesModelURL_forSofa() {
        val bytes = fixtureBytes("test_small.sh3d")
        val cache = tmp.newFolder("reader")
        val home = SH3DReader.read(bytes, cache)
        val sofa = home.furniture.first { it.name == "Sofa" || it.catalogID?.contains("sofa") == true }
        assertEquals("1", sofa.modelRef)
        assertNotNull(sofa.modelURL)
        assertTrue(File(sofa.modelURL!!).isFile)
        assertTrue(sofa.modelURL!!.endsWith(".obj"))
        assertTrue(home.extractedAssetURLs.containsKey("1"))
    }

    @Test
    fun sniffExtension_detectsObjPngAndMtl() {
        assertEquals("png", SH3DMeshExtraction.sniffExtension(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0, 0, 0, 0),
        ))
        assertEquals(
            "obj",
            SH3DMeshExtraction.sniffExtension("# sofa.obj\nv 0 0 0\nf 1 1 1\n".toByteArray()),
        )
        assertEquals(
            "mtl",
            SH3DMeshExtraction.sniffExtension("newmtl wood\nKd 1 1 1\nmap_Kd wood.jpg\n".toByteArray()),
        )
        assertEquals(null, SH3DMeshExtraction.sniffExtension(byteArrayOf(0, 1, 2, 3)))
    }

    @Test
    fun sanitizeRelativePath_rejectsTraversal() {
        assertEquals(null, SH3DMeshExtraction.sanitizeRelativePath("../etc/passwd"))
        assertEquals(null, SH3DMeshExtraction.sanitizeRelativePath("/abs"))
        assertEquals("16/throwPillow.obj", SH3DMeshExtraction.sanitizeRelativePath("16/throwPillow.obj"))
    }

    private fun fixtureBytes(name: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream(name)!!.use { it.readBytes() }
}
