package com.homedesign.android.domain.io

import com.homedesign.android.domain.model.Home
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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
    val textures: Map<String, ByteArray>? = null,
)

object HomedesignArchive {
    fun write(home: Home, options: HomeWriteOptions = HomeWriteOptions()): ByteArray {
        val manifest = encodeHome(home)
        val thumbnail = options.thumbnail ?: PLACEHOLDER_PNG
        val entries = mutableMapOf(
            "format_version" to FORMAT_VERSION.toByteArray(Charsets.UTF_8),
            "manifest.json" to manifest.toByteArray(Charsets.UTF_8),
            "thumbnail.png" to thumbnail,
        )
        return writeZip(entries)
    }

    fun read(data: ByteArray): Home {
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
        return try {
            decodeHome(manifestBytes)
        } catch (e: Exception) {
            throw MalformedManifestException(e.message ?: "decodeHome failed")
        }
    }
}

/** Stage-1 API alias used by repository / sketch import. */
object HomedesignZip {
    fun encode(home: Home, options: HomeWriteOptions = HomeWriteOptions()): ByteArray =
        HomedesignArchive.write(home, options)

    fun decode(data: ByteArray): Home = HomedesignArchive.read(data)
}

/** CRC helper for store method tests. */
fun crc32(bytes: ByteArray): Long {
    val crc = CRC32()
    crc.update(bytes)
    return crc.value
}
