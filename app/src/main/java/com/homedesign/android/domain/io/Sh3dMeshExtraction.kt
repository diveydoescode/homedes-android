package com.homedesign.android.domain.io

import java.io.File
import java.util.Locale

/**
 * Extract embedded mesh / texture / icon entries from a `.sh3d` ZIP into a
 * per-home cache directory (iOS `SH3DMeshExtraction` port).
 *
 * Flat numbered entries (`0`, `1`, …) are content-sniffed and written with
 * `.png` / `.jpg` / `.obj` extensions so loaders can dispatch by suffix.
 * Subdir entries (`16/throwPillow.obj`) keep their paths verbatim.
 *
 * Skipped: `Home.xml` (parsed separately), legacy Java `Home` blob,
 * `ContentDigests` (+ children), and directory markers.
 *
 * Returned map keys are the raw ZIP entry names (Home.xml `model=` /
 * `icon=` values); values are absolute on-disk paths (with sniffed ext).
 */
object SH3DMeshExtraction {

    private val skippedExact = setOf("Home", "Home.xml", "ContentDigests")

    fun extractAll(archiveBytes: ByteArray, baseDir: File): Map<String, String> {
        val entries = unwrapSingleRootFolder(readZip(archiveBytes))
        if (!baseDir.exists() && !baseDir.mkdirs()) {
            return emptyMap()
        }
        val out = LinkedHashMap<String, String>()
        for ((name, payload) in entries) {
            if (shouldSkip(name)) continue
            val safeRel = sanitizeRelativePath(name) ?: continue
            val resolvedRel = sniffedRelativePath(safeRel, payload)
            val dest = File(baseDir, resolvedRel)
            val parent = dest.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()
            // Refuse zip-slip escapes.
            if (!dest.canonicalPath.startsWith(baseDir.canonicalPath + File.separator) &&
                dest.canonicalPath != baseDir.canonicalPath
            ) {
                continue
            }
            dest.writeBytes(payload)
            out[name] = dest.absolutePath
        }
        return out
    }

    fun shouldSkip(entryName: String): Boolean {
        val n = normalizeZipPath(entryName)
        if (n in skippedExact) return true
        if (n.startsWith("ContentDigests/")) return true
        if (n.endsWith("/")) return true
        return false
    }

    /** Append sniffed extension when the last path component has none. */
    fun sniffedRelativePath(entryName: String, payload: ByteArray): String {
        val n = normalizeZipPath(entryName)
        val slash = n.lastIndexOf('/')
        val last = if (slash >= 0) n.substring(slash + 1) else n
        if (last.contains('.') && !last.startsWith(".")) return n
        val ext = sniffExtension(payload) ?: return n
        return "$n.$ext"
    }

    fun sniffExtension(payload: ByteArray): String? {
        if (payload.size >= 4) {
            val b0 = payload[0].toInt() and 0xFF
            val b1 = payload[1].toInt() and 0xFF
            val b2 = payload[2].toInt() and 0xFF
            val b3 = payload[3].toInt() and 0xFF
            if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) return "png"
            if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) return "jpg"
        }
        val prefixLen = minOf(200, payload.size)
        if (prefixLen == 0) return null
        val text = runCatching {
            String(payload, 0, prefixLen, Charsets.UTF_8)
        }.getOrNull() ?: return null
        var sawObj = false
        var sawMtl = false
        for (raw in text.splitToSequence('\n', '\r')) {
            val l = raw.trimStart()
            if (l.startsWith("newmtl") || l.startsWith("map_Kd") || l.startsWith("map_kd") ||
                l.startsWith("Kd ") || l.startsWith("Ka ") || l.startsWith("Ns ")
            ) {
                sawMtl = true
            }
            if (l.startsWith("#") ||
                l.startsWith("v ") ||
                l.startsWith("vn ") ||
                l.startsWith("vt ") ||
                l.startsWith("g ") ||
                l.startsWith("o ") ||
                l.startsWith("f ") ||
                l.startsWith("mtllib") ||
                l.startsWith("usemtl")
            ) {
                sawObj = true
            }
        }
        // Prefer OBJ when both markers appear (comments can mention materials).
        if (sawObj) return "obj"
        if (sawMtl) return "mtl"
        return null
    }

    /**
     * Reject empty / absolute / `..` traversal paths. Returns normalized
     * relative path or null.
     */
    fun sanitizeRelativePath(entryName: String): String? {
        val n = normalizeZipPath(entryName)
        if (n.isEmpty() || n.startsWith("/") || n.contains(":")) return null
        val parts = n.split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty() || parts.any { it == "." || it == ".." }) return null
        return parts.joinToString("/")
    }

    /** True when [path] looks like a loadable Wavefront OBJ (by suffix). */
    fun isObjPath(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        return path.lowercase(Locale.US).endsWith(".obj")
    }
}
