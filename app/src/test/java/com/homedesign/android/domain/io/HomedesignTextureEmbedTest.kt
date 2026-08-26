package com.homedesign.android.domain.io

import com.homedesign.android.domain.model.HomeFactory
import com.homedesign.android.domain.model.Point
import com.homedesign.android.domain.model.Room
import com.homedesign.android.domain.model.Wall
import com.homedesign.android.domain.model.WallTexture
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HomedesignTextureEmbedTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun texturedHome(wallHandle: String, floorHandle: String) =
        HomeFactory.emptyHome("Tex").copy(
            walls = listOf(
                Wall(
                    id = "w",
                    startX = 0.0,
                    startY = 0.0,
                    endX = 400.0,
                    endY = 0.0,
                    thickness = 10.0,
                    height = 250.0,
                    leftSideTexture = WallTexture(
                        catalogID = wallHandle,
                        image = wallHandle,
                        width = 50.0,
                        height = 50.0,
                    ),
                ),
            ),
            rooms = listOf(
                Room(
                    id = "r",
                    points = listOf(
                        Point(0.0, 0.0),
                        Point(400.0, 0.0),
                        Point(400.0, 300.0),
                        Point(0.0, 300.0),
                    ),
                    floorTexture = WallTexture(
                        catalogID = floorHandle,
                        image = floorHandle,
                        width = 80.0,
                        height = 80.0,
                    ),
                ),
            ),
        )

    private fun tempImage(bytes: ByteArray, ext: String): File {
        val file = tmp.newFile("emb-tex-${System.nanoTime()}.$ext")
        file.writeBytes(bytes)
        return file
    }

    @Test
    fun writeEmbedsAndReadRestoresTextures() {
        val wallBytes = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        val floorBytes = byteArrayOf(1, 2, 3, 4, 5, 6)
        val wallFile = tempImage(wallBytes, "jpg")
        val floorFile = tempImage(floorBytes, "png")
        val home = texturedHome("user:abc", "42").let { base ->
            val wall = base.walls[0]
            val room = base.rooms[0]
            base.copy(
                walls = listOf(
                    wall.copy(
                        leftSideTexture = wall.leftSideTexture!!.copy(image = wallFile.absolutePath),
                    ),
                ),
                rooms = listOf(
                    room.copy(
                        floorTexture = room.floorTexture!!.copy(image = floorFile.absolutePath),
                    ),
                ),
                extractedAssetURLs = mapOf(
                    "user:abc" to wallFile.absolutePath,
                    "42" to floorFile.absolutePath,
                    wallFile.absolutePath to wallFile.absolutePath,
                    floorFile.absolutePath to floorFile.absolutePath,
                ),
            )
        }

        val archive = HomedesignZip.encode(home)
        val entries = unwrapSingleRootFolder(readZip(archive))
        assertTrue(entries.containsKey("assets/textures/index.json"))
        // Simulate sharing onto another device: original files are gone.
        assertTrue(wallFile.delete())
        assertTrue(floorFile.delete())

        val restoredDir = tmp.newFolder("restored")
        val restored = HomedesignZip.decode(archive, restoredDir)
        val wallPath = restored.extractedAssetURLs["user:abc"]
            ?: restored.walls[0].leftSideTexture?.image
        val floorPath = restored.extractedAssetURLs["42"]
            ?: restored.rooms[0].floorTexture?.image
        assertNotNull(wallPath)
        assertNotNull(floorPath)
        assertTrue(File(wallPath!!).isFile)
        assertTrue(File(floorPath!!).isFile)
        assertArrayEquals(wallBytes, File(wallPath).readBytes())
        assertArrayEquals(floorBytes, File(floorPath).readBytes())
        assertTrue(wallPath.endsWith(".jpg"))
        assertTrue(floorPath.endsWith(".png"))
        assertEquals(wallPath, restored.walls[0].leftSideTexture?.image)
        assertEquals(floorPath, restored.rooms[0].floorTexture?.image)
    }

    @Test
    fun presetHandlesAreNotEmbedded() {
        val home = texturedHome("preset:slate", "preset:oak").copy(
            extractedAssetURLs = mapOf(
                "preset:slate" to tempImage(byteArrayOf(9, 9, 9), "jpg").absolutePath,
            ),
        )
        val entries = HomedesignArchive.textureEntries(home)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun unresolvedHandlesAreSkippedNotFatal() {
        val here = tempImage(byteArrayOf(7, 7), "jpg")
        val home = texturedHome("user:gone", "user:here").let { base ->
            val room = base.rooms[0]
            base.copy(
                rooms = listOf(
                    room.copy(
                        floorTexture = room.floorTexture!!.copy(image = here.absolutePath),
                    ),
                ),
                extractedAssetURLs = mapOf("user:here" to here.absolutePath),
            )
        }
        val archive = HomedesignZip.encode(home)
        assertTrue(here.delete())
        val restored = HomedesignZip.decode(archive, tmp.newFolder("partial"))
        assertNull(restored.extractedAssetURLs["user:gone"])
        assertNotNull(restored.extractedAssetURLs["user:here"] ?: restored.rooms[0].floorTexture?.image)
        assertTrue(File(restored.rooms[0].floorTexture!!.image!!).isFile)
    }

    @Test
    fun duplicateHandlesEmbedOnce() {
        val file = tempImage(byteArrayOf(5, 5, 5), "jpg")
        val home = texturedHome("user:same", "user:same").let { base ->
            val wall = base.walls[0]
            val room = base.rooms[0]
            base.copy(
                walls = listOf(
                    wall.copy(leftSideTexture = wall.leftSideTexture!!.copy(image = file.absolutePath)),
                ),
                rooms = listOf(
                    room.copy(floorTexture = room.floorTexture!!.copy(image = file.absolutePath)),
                ),
                extractedAssetURLs = mapOf("user:same" to file.absolutePath),
            )
        }
        val entries = HomedesignArchive.textureEntries(home)
        assertEquals(2, entries.size)
        assertTrue(entries.containsKey("assets/textures/index.json"))
    }

    @Test
    fun extraBytesMapEmbedsWithoutOnDiskFile() {
        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0, 0, 0, 0)
        val home = texturedHome("user:mem", "preset:oak")
        val archive = HomedesignZip.encode(
            home,
            HomeWriteOptions(textures = mapOf("user:mem" to bytes)),
        )
        val restored = HomedesignZip.decode(archive, tmp.newFolder("mem"))
        val path = restored.extractedAssetURLs["user:mem"] ?: restored.walls[0].leftSideTexture?.image
        assertNotNull(path)
        assertArrayEquals(bytes, File(path!!).readBytes())
        assertTrue(path.endsWith(".png") || path.endsWith(".img"))
    }

    @Test
    fun fileSchemePathsAreReadAndRebound() {
        val raw = byteArrayOf(10, 20, 30, 40)
        val file = tempImage(raw, "jpg")
        val uri = file.toURI().toString()
        val home = texturedHome("user:uri", "preset:oak").let { base ->
            val wall = base.walls[0]
            base.copy(
                walls = listOf(
                    wall.copy(leftSideTexture = wall.leftSideTexture!!.copy(image = uri)),
                ),
                extractedAssetURLs = mapOf("user:uri" to uri),
            )
        }
        val archive = HomedesignZip.encode(home)
        assertTrue(file.delete())
        val restored = HomedesignZip.decode(archive, tmp.newFolder("uri"))
        val path = restored.walls[0].leftSideTexture?.image
        assertNotNull(path)
        assertFalse(path!!.startsWith("file:"))
        assertArrayEquals(raw, File(path).readBytes())
    }

    @Test
    fun legacyArchiveWithoutAssetsStillLoads() {
        val home = HomeFactory.emptyHome("Legacy")
        val entries = mapOf(
            "format_version" to FORMAT_VERSION.toByteArray(Charsets.UTF_8),
            "manifest.json" to encodeHome(home).toByteArray(Charsets.UTF_8),
            "thumbnail.png" to PLACEHOLDER_PNG,
        )
        val restored = HomedesignZip.decode(writeZip(entries))
        assertTrue(restored.extractedAssetURLs.isEmpty())
        assertEquals("Legacy", restored.name)
    }

    @Test
    fun zipSlipIndexFilenameIsIgnored() {
        val home = HomeFactory.emptyHome("Safe")
        val evilIndex = """[{"handle":"user:x","filename":"assets/textures/../../evil.bin"}]"""
        val entries = mapOf(
            "format_version" to FORMAT_VERSION.toByteArray(Charsets.UTF_8),
            "manifest.json" to encodeHome(home).toByteArray(Charsets.UTF_8),
            "thumbnail.png" to PLACEHOLDER_PNG,
            "assets/textures/index.json" to evilIndex.toByteArray(Charsets.UTF_8),
            "assets/textures/../../evil.bin" to byteArrayOf(1, 2, 3),
        )
        val dest = tmp.newFolder("safe")
        val restored = HomedesignZip.decode(writeZip(entries), dest)
        assertNull(restored.extractedAssetURLs["user:x"])
        assertFalse(File(dest, "evil.bin").exists())
        assertTrue(isSafeTextureArchivePath("assets/textures/0.jpg"))
        assertFalse(isSafeTextureArchivePath("assets/textures/../../evil.bin"))
    }
}
