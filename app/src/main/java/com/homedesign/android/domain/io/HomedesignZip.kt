package com.homedesign.android.domain.io

import com.homedesign.android.domain.model.Baseboard
import com.homedesign.android.domain.model.Home
import com.homedesign.android.domain.model.WallTexture
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val FORMAT_VERSION = "1"

typealias ZipEntries = Map<String, ByteArray>

class InvalidArchiveException(message: String) : Exception(message)
class MissingFormatVersionException(message: String) : Exception(message)
class MissingManifestException(message: String = "manifest.json missing") : Exception(message)
class UnsupportedFormatVersionException(actual: String, expected: String) :
    Exception("unsupported format_version '$actual' (expected '$expected')")
class MalformedManifestException(message: String) : Exception(message)

fun normalizeZipPath(name: String): String {
    var path = name.replace('\\', '/')
    if (path.startsWith("./")) path = path.drop(2)
    return path
}

fun isIgnoredZipEntry(name: String): Boolean {
    val parts = normalizeZipPath(name).split('/').filter { it.isNotEmpty() }
    return parts.any { it == "__MACOSX" || it.startsWith("._") }
}

fun unwrapSingleRootFolder(entries: ZipEntries): ZipEntries {
    if (entries.isEmpty()) return entries
    val tops = mutableSetOf<String>()
    for (key in entries.keys) {
        val slash = key.indexOf('/')
        if (slash <= 0) return entries
        tops.add(key.substring(0, slash))
    }
    if (tops.size != 1) return entries
    val prefix = "${tops.first()}/"
    val out = mutableMapOf<String, ByteArray>()
    for ((key, payload) in entries) {
        val rest = key.removePrefix(prefix)
        if (rest.isEmpty() || rest.contains("..")) continue
        out[rest] = payload
    }
    return if (out.isNotEmpty()) out else entries
}

fun readZip(data: ByteArray): ZipEntries {
    if (data.size < 4 || data[0] != 0x50.toByte() || data[1] != 0x4b.toByte()) {
        throw InvalidArchiveException("not a ZIP archive")
    }
    val entries = mutableMapOf<String, ByteArray>()
    ZipInputStream(ByteArrayInputStream(data)).use { zis ->
        while (true) {
            val entry = zis.nextEntry ?: break
            val key = normalizeZipPath(entry.name)
            if (key.isEmpty() || key.endsWith("/") || isIgnoredZipEntry(key)) {
                zis.closeEntry()
                continue
            }
            entries[key] = zis.readBytes()
            zis.closeEntry()
        }
    }
    return entries
}

fun writeZip(entries: ZipEntries): ByteArray {
    val baos = ByteArrayOutputStream()
    ZipOutputStream(baos).use { zos ->
        for ((name, bytes) in entries) {
            val entry = ZipEntry(normalizeZipPath(name))
            entry.method = ZipEntry.DEFLATED
            zos.putNextEntry(entry)
            zos.write(bytes)
            zos.closeEntry()
        }
    }
    return baos.toByteArray()
}

/** Smallest valid 1×1 transparent PNG placeholder. */
val PLACEHOLDER_PNG = byteArrayOf(
    0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00, 0x00, 0x00, 0x0d,
    0x49, 0x48, 0x44, 0x52, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
    0x08, 0x06, 0x00, 0x00, 0x00, 0x1f, 0x15, 0xc4.toByte(), 0x89.toByte(), 0x00, 0x00, 0x00,
    0x0d, 0x49, 0x44, 0x41, 0x54, 0x78, 0x9c.toByte(), 0x62, 0x00, 0x01, 0x00, 0x00,
    0x05, 0x00, 0x01, 0x0d, 0x0a, 0x2d, 0xb4.toByte(), 0x00, 0x00, 0x00, 0x00, 0x49,
    0x45, 0x4e, 0x44, 0xae.toByte(), 0x42, 0x60, 0x82.toByte(),
)

data class HomeWriteOptions(
    val thumbnail: ByteArray? = null,
    /**
     * Optional texture bytes keyed by handle (`user:<uuid>`, SH3D id, or a
     * local file path). When null, [textureEntries] reads files from
     * [Home.extractedAssetURLs] and [WallTexture.image] paths.
     */
    val textures: Map<String, ByteArray>? = null,
)

/** One row of `assets/textures/index.json` (iOS / web X-5 contract). */
@Serializable
data class TextureIndexEntry(
    val handle: String,
    val filename: String,
)

private val textureIndexJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

private const val TEXTURE_INDEX_PATH = "assets/textures/index.json"
private const val TEXTURE_ENTRY_PREFIX = "assets/textures/"
private const val PRESET_HANDLE_PREFIX = "preset:"

object HomedesignArchive {
    fun write(home: Home, options: HomeWriteOptions = HomeWriteOptions()): ByteArray {
        val manifest = encodeHome(home)
        val thumbnail = options.thumbnail ?: PLACEHOLDER_PNG
        val entries = mutableMapOf(
            "format_version" to FORMAT_VERSION.toByteArray(Charsets.UTF_8),
            "manifest.json" to manifest.toByteArray(Charsets.UTF_8),
            "thumbnail.png" to thumbnail,
        )
        entries.putAll(textureEntries(home, options.textures))
        return writeZip(entries)
    }

    fun read(data: ByteArray, textureDirectory: File? = null): Home {
        val entries = unwrapSingleRootFolder(readZip(data))
        val versionBytes = entries["format_version"]
            ?: throw MissingFormatVersionException("missing format_version")
        val actualVersion = versionBytes.toString(Charsets.UTF_8).trim()
        if (actualVersion.isEmpty()) {
            throw MalformedManifestException("format_version not readable as UTF-8")
        }
        if (actualVersion != FORMAT_VERSION) {
            throw UnsupportedFormatVersionException(actualVersion, FORMAT_VERSION)
        }
        val manifestBytes = entries["manifest.json"]
            ?: throw MissingManifestException()
        val home = try {
            decodeHome(manifestBytes)
        } catch (e: Exception) {
            throw MalformedManifestException(e.message ?: "decodeHome failed")
        }
        return restoreEmbeddedTextures(entries, home, textureDirectory)
    }

    /** Rooms' floor/ceiling/border + walls' sides and baseboards. */
    fun collectSurfaceTextures(home: Home): List<WallTexture?> {
        val textures = ArrayList<WallTexture?>(
            home.rooms.size * 3 + home.walls.size * 4,
        )
        for (room in home.rooms) {
            textures.add(room.floorTexture)
            textures.add(room.ceilingTexture)
            textures.add(room.borderTexture)
        }
        for (wall in home.walls) {
            textures.add(wall.leftSideTexture)
            textures.add(wall.rightSideTexture)
            textures.add(wall.leftSideBaseboard?.texture)
            textures.add(wall.rightSideBaseboard?.texture)
        }
        return textures
    }

    /**
     * One ZIP entry per resolvable non-preset texture, plus `index.json`.
     * Presets are skipped (every install bundles them). Missing files are
     * skipped so a broken handle never fails the archive write.
     */
    fun textureEntries(
        home: Home,
        extraBytes: Map<String, ByteArray>? = null,
    ): Map<String, ByteArray> {
        val seen = HashSet<String>()
        val index = ArrayList<TextureIndexEntry>()
        val out = LinkedHashMap<String, ByteArray>()
        for (texture in collectSurfaceTextures(home)) {
            val handle = textureIndexHandle(texture) ?: continue
            if (!seen.add(handle)) continue
            val resolved = resolveTextureBytes(texture, handle, home, extraBytes) ?: continue
            val filename = "$TEXTURE_ENTRY_PREFIX${index.size}.${resolved.extension}"
            out[filename] = resolved.bytes
            index.add(TextureIndexEntry(handle = handle, filename = filename))
        }
        if (index.isEmpty()) return emptyMap()
        val encoded = runCatching {
            textureIndexJson.encodeToString(index)
        }.getOrNull() ?: return emptyMap()
        out[TEXTURE_INDEX_PATH] = encoded.toByteArray(Charsets.UTF_8)
        return out
    }
}

/** Stage-1 API alias used by repository / sketch import. */
object HomedesignZip {
    fun encode(home: Home, options: HomeWriteOptions = HomeWriteOptions()): ByteArray =
        HomedesignArchive.write(home, options)

    fun decode(data: ByteArray, textureDirectory: File? = null): Home =
        HomedesignArchive.read(data, textureDirectory)

    /** Fresh subdir under [filesDir] for extracted texture images. */
    fun embeddedTextureDirectory(filesDir: File): File =
        File(filesDir, "HomeDesignEmbeddedTextures/${UUID.randomUUID()}")
}

/**
 * Extract `assets/textures/` entries and rebind [WallTexture.image] /
 * extracted URLs onto files under [textureDirectory]. No-op for pre-X-5
 * archives. Never throws — a broken blob leaves its handle unresolved.
 */
internal fun restoreEmbeddedTextures(
    entries: ZipEntries,
    home: Home,
    textureDirectory: File?,
): Home {
    val indexBytes = entries[TEXTURE_INDEX_PATH] ?: return home
    val index = parseTextureIndex(indexBytes)
    if (index.isEmpty()) return home
    val dir = textureDirectory ?: File(
        System.getProperty("java.io.tmpdir") ?: ".",
        "HomeDesignEmbeddedTextures/${UUID.randomUUID()}",
    )
    if (!dir.exists() && !dir.mkdirs()) return home
    val urls = home.extractedAssetURLs.toMutableMap()
    val rebound = LinkedHashMap<String, String>()
    for (entry in index) {
        if (!isSafeTextureArchivePath(entry.filename)) continue
        val existing = urls[entry.handle]?.let { readableTextureFile(it) }
        if (existing != null) {
            rebound[entry.handle] = existing.absolutePath
            continue
        }
        val blob = entries[normalizeZipPath(entry.filename)] ?: continue
        val dest = safeTextureDest(dir, entry.filename) ?: continue
        val written = runCatching {
            dest.parentFile?.mkdirs()
            dest.writeBytes(blob)
            dest
        }.getOrNull() ?: continue
        val path = written.absolutePath
        urls[entry.handle] = path
        urls[path] = path
        rebound[entry.handle] = path
    }
    if (rebound.isEmpty() && urls == home.extractedAssetURLs) return home
    return rebindHomeTextures(home, rebound, urls)
}

private fun rebindHomeTextures(
    home: Home,
    rebound: Map<String, String>,
    urls: MutableMap<String, String>,
): Home {
    fun tex(texture: WallTexture?): WallTexture? {
        if (texture == null) return null
        val image = texture.image
        if (image != null && image.startsWith(PRESET_HANDLE_PREFIX)) return texture
        val newPath = rebound[image]
            ?: rebound[texture.catalogID]
            ?: image?.let { urls[it] }?.takeIf { readableTextureFile(it) != null }
            ?: texture.catalogID?.let { urls[it] }?.takeIf { readableTextureFile(it) != null }
            ?: return texture
        texture.catalogID
            ?.takeIf { it.isNotEmpty() && !it.startsWith(PRESET_HANDLE_PREFIX) }
            ?.let { urls[it] = newPath }
        if (image == newPath) return texture
        return texture.copy(image = newPath)
    }
    fun board(board: Baseboard?): Baseboard? =
        board?.let { it.copy(texture = tex(it.texture)) }
    return home.copy(
        extractedAssetURLs = urls,
        walls = home.walls.map { wall ->
            wall.copy(
                leftSideTexture = tex(wall.leftSideTexture),
                rightSideTexture = tex(wall.rightSideTexture),
                leftSideBaseboard = board(wall.leftSideBaseboard),
                rightSideBaseboard = board(wall.rightSideBaseboard),
            )
        },
        rooms = home.rooms.map { room ->
            room.copy(
                floorTexture = tex(room.floorTexture),
                ceilingTexture = tex(room.ceilingTexture),
                borderTexture = tex(room.borderTexture),
            )
        },
    )
}

internal fun textureIndexHandle(texture: WallTexture?): String? {
    if (texture == null) return null
    val image = texture.image?.takeIf { it.isNotEmpty() }
    val catalog = texture.catalogID?.takeIf { it.isNotEmpty() }
    val raw = image ?: catalog ?: return null
    if (shouldSkipTextureHandle(raw)) return null
    return raw
}

private fun shouldSkipTextureHandle(handle: String): Boolean {
    if (handle.isEmpty() || handle.startsWith(PRESET_HANDLE_PREFIX)) return true
    // Bundled APK assets (`textures/floors/…`) — not embeddable here.
    if (handle.startsWith("textures/")) return true
    return false
}

private data class ResolvedTextureBytes(val bytes: ByteArray, val extension: String)

private fun resolveTextureBytes(
    texture: WallTexture?,
    handle: String,
    home: Home,
    extraBytes: Map<String, ByteArray>?,
): ResolvedTextureBytes? {
    val candidates = listOfNotNull(handle, texture?.image, texture?.catalogID)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
    if (extraBytes != null) {
        for (key in candidates) {
            val bytes = extraBytes[key] ?: continue
            if (bytes.isEmpty()) continue
            return ResolvedTextureBytes(bytes, textureExtension(bytes, key))
        }
    }
    for (key in candidates) {
        val fromMap = home.extractedAssetURLs[key]?.let { readableTextureFile(it) }
        val file = fromMap ?: readableTextureFile(key) ?: continue
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: continue
        if (bytes.isEmpty()) continue
        return ResolvedTextureBytes(bytes, textureExtension(bytes, file.name))
    }
    return null
}

internal fun readableTextureFile(raw: String): File? {
    if (raw.isBlank() || raw.startsWith(PRESET_HANDLE_PREFIX)) return null
    if (raw.startsWith("file:")) {
        val fromUri = runCatching { File(URI(raw)) }.getOrNull()
        if (fromUri != null && fromUri.isFile) return fromUri
        val stripped = raw.removePrefix("file:").removePrefix("//")
        val file = File(stripped)
        return file.takeIf { it.isFile }
    }
    return File(raw).takeIf { it.isFile }
}

internal fun isSafeTextureArchivePath(name: String): Boolean {
    val n = normalizeZipPath(name)
    if (!n.startsWith(TEXTURE_ENTRY_PREFIX)) return false
    val rest = n.removePrefix(TEXTURE_ENTRY_PREFIX)
    if (rest.isEmpty() || rest.contains('/') || rest == "." || rest == "..") return false
    if (rest.contains("..") || rest.contains('\\')) return false
    return true
}

private fun safeTextureDest(dir: File, archivePath: String): File? {
    val name = normalizeZipPath(archivePath).substringAfterLast('/')
    if (name.isEmpty() || name == "." || name == "..") return null
    val dest = File(dir, name)
    val dirCanon = dir.canonicalFile
    val destCanon = dest.canonicalFile
    val prefix = dirCanon.path + File.separator
    if (destCanon != dirCanon && !destCanon.path.startsWith(prefix)) return null
    return dest
}

private fun textureExtension(bytes: ByteArray, sourceName: String?): String {
    val fromName = sourceName
        ?.substringAfterLast('.')
        ?.lowercase()
        ?.takeIf { it in TEXTURE_EXTENSIONS }
    if (fromName != null) return if (fromName == "jpeg") "jpg" else fromName
    if (bytes.size >= 8 &&
        bytes[0] == 0x89.toByte() &&
        bytes[1] == 0x50.toByte() &&
        bytes[2] == 0x4E.toByte() &&
        bytes[3] == 0x47.toByte()
    ) {
        return "png"
    }
    if (bytes.size >= 3 &&
        bytes[0] == 0xFF.toByte() &&
        bytes[1] == 0xD8.toByte() &&
        bytes[2] == 0xFF.toByte()
    ) {
        return "jpg"
    }
    return "img"
}

private val TEXTURE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "img")

private fun parseTextureIndex(bytes: ByteArray): List<TextureIndexEntry> {
    return runCatching {
        textureIndexJson.decodeFromString<List<TextureIndexEntry>>(
            bytes.toString(Charsets.UTF_8),
        )
    }.getOrDefault(emptyList()).filter { it.handle.isNotEmpty() && it.filename.isNotEmpty() }
}

/** CRC helper for store method tests. */
fun crc32(bytes: ByteArray): Long {
    val crc = CRC32()
    crc.update(bytes)
    return crc.value
}
