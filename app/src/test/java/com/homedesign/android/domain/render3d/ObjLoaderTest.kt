package com.homedesign.android.domain.render3d

import com.homedesign.android.domain.io.SH3DReader
import com.homedesign.android.domain.model.HomePieceOfFurniture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ObjLoaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun loadsSofaFromTestSmall() {
        val bytes = javaClass.classLoader!!.getResourceAsStream("test_small.sh3d")!!.use { it.readBytes() }
        val home = SH3DReader.read(bytes, tmp.newFolder("m"))
        val sofa = home.furniture.first { it.name == "Sofa" }
        val meshes = ObjLoader.loadAsFurniture(sofa.modelURL!!, sofa, 0xFFB85C3C.toInt())
        assertNotNull(meshes)
        assertTrue(meshes!!.isNotEmpty())
        assertTrue(meshes.first().positions.size >= 9)
        assertEquals(meshes.first().positions.size, meshes.first().normals.size)
    }

    @Test
    fun extrusionUsesObjWhenModelURLSet() {
        val bytes = javaClass.classLoader!!.getResourceAsStream("test_small.sh3d")!!.use { it.readBytes() }
        val home = SH3DReader.read(bytes, tmp.newFolder("e"))
        val scene = HomeExtrusion.build(home)
        assertTrue(scene.meshes.isNotEmpty())
        // Sofa OBJ is much denser than a procedural box (12 tris).
        val maxVerts = scene.meshes.maxOf { it.positions.size / 3 }
        assertTrue("expected dense OBJ mesh, maxVerts=$maxVerts", maxVerts > 100)
    }

    @Test
    fun missingFileReturnsNull() {
        val piece = HomePieceOfFurniture(
            id = "p",
            x = 0.0,
            y = 0.0,
            width = 100.0,
            depth = 50.0,
            height = 80.0,
            modelURL = File(tmp.root, "missing.obj").absolutePath,
        )
        assertTrue(ObjLoader.loadAsFurniture(piece.modelURL!!, piece, 0xFF000000.toInt()) == null)
    }

    @Test
    fun loadsMapKdTextureAndUvs() {
        val dir = tmp.newFolder("texobj")
        // Minimal 1×1 opaque PNG.
        val png = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x02, 0x00, 0x00, 0x00, 0x90.toByte(), 0x77, 0x53,
            0xDE.toByte(), 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,
            0x54, 0x08, 0xD7.toByte(), 0x63, 0xF8.toByte(), 0xCF.toByte(), 0xC0.toByte(), 0x00,
            0x00, 0x00, 0x03, 0x00, 0x01, 0x00, 0x05, 0xFE.toByte(),
            0xD4.toByte(), 0xEF.toByte(), 0x00, 0x00, 0x00, 0x00, 0x49, 0x45,
            0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
        )
        File(dir, "wood.png").writeBytes(png)
        File(dir, "box.mtl").writeText(
            """
            newmtl wood
            Kd 1 1 1
            map_Kd -o 0 0 wood.png
            """.trimIndent(),
        )
        File(dir, "box.obj").writeText(
            """
            mtllib box.mtl
            v 0 0 0
            v 1 0 0
            v 1 1 0
            v 0 1 0
            vt 0 0
            vt 1 0
            vt 1 1
            vt 0 1
            vn 0 0 1
            usemtl wood
            f 1/1/1 2/2/1 3/3/1
            f 1/1/1 3/3/1 4/4/1
            """.trimIndent(),
        )
        val piece = HomePieceOfFurniture(
            id = "box",
            x = 50.0,
            y = 40.0,
            width = 80.0,
            depth = 60.0,
            height = 40.0,
            modelURL = File(dir, "box.obj").absolutePath,
        )
        val meshes = ObjLoader.loadAsFurniture(piece.modelURL!!, piece, 0xFF888888.toInt())
        assertNotNull(meshes)
        assertEquals(1, meshes!!.size)
        val mesh = meshes.first()
        assertNotNull(mesh.uvs)
        assertEquals(mesh.positions.size / 3 * 2, mesh.uvs!!.size)
        assertNotNull(mesh.textureAssetPath)
        assertTrue(mesh.textureAssetPath!!.startsWith("file:"))
        assertTrue(File(mesh.textureAssetPath!!.removePrefix("file:")).isFile)
    }

    @Test
    fun lastMapKdTokenSkipsOptions() {
        assertEquals("wood.jpg", ObjLoader.lastMapKdToken("-o 1 1 wood.jpg"))
        assertEquals("a/b.png", ObjLoader.lastMapKdToken("a/b.png"))
        assertNull(ObjLoader.lastMapKdToken(""))
    }
}
