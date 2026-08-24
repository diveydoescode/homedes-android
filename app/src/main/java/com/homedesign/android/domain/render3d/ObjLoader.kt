package com.homedesign.android.domain.render3d

import com.homedesign.android.domain.model.HomePieceOfFurniture
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Minimal Wavefront OBJ → [MeshTri] loader for SH3D-embedded furniture.
 * Parses geometry, normals, UVs, `mtllib` / `usemtl`, and MTL `map_Kd`
 * (diffuse texture). Textured groups get `file:` absolute [MeshTri.textureAssetPath].
 * Scales mesh bounds to the piece footprint and places it in plan space
 * (cm → metres, Y-up) matching [HomeExtrusion] / iOS FurnitureTransform.
 */
object ObjLoader {
    private const val CM_TO_M = 0.01f
    private const val MAX_TRIANGLES = 80_000
    private const val FLOOR_CLEARANCE_CM = 0.15

    /**
     * Load [path] and fit it to [piece]. Returns null on missing file,
     * unreadable OBJ, or empty geometry (caller falls back to procedural).
     * May return multiple meshes when materials use different `map_Kd` textures.
     */
    fun loadAsFurniture(
        path: String,
        piece: HomePieceOfFurniture,
        colorArgb: Int,
        roughness: Float = 0.55f,
    ): List<MeshTri>? {
        val file = File(path)
        if (!file.isFile) return null
        val parsed = parse(file) ?: return null
        if (parsed.corners.isEmpty()) return null

        val width = (piece.widthInPlan ?: piece.width).toFloat().coerceAtLeast(1e-3f)
        val height = piece.height.toFloat().coerceAtLeast(1e-3f)
        val depth = (piece.depthInPlan ?: piece.depth).toFloat().coerceAtLeast(1e-3f)

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        for (c in parsed.corners) {
            val vi = c.posIdx * 3
            if (vi + 2 >= parsed.positions.size) continue
            val x = parsed.positions[vi]
            val y = parsed.positions[vi + 1]
            val z = parsed.positions[vi + 2]
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (z < minZ) minZ = z
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
            if (z > maxZ) maxZ = z
        }
        if (!minX.isFinite()) return null
        val extX = (maxX - minX).let { if (abs(it) < 1e-8f) 1f else it }
        val extY = (maxY - minY).let { if (abs(it) < 1e-8f) 1f else it }
        val extZ = (maxZ - minZ).let { if (abs(it) < 1e-8f) 1f else it }
        val sx = width / extX
        val sy = height / extY
        val sz = depth / extZ
        val centerX = (minX + maxX) * 0.5f
        val centerZ = (minZ + maxZ) * 0.5f

        val mx = if (piece.modelMirrored) -1f else 1f
        val rot = piece.modelRotation
        val hasRot = rot != null && rot.size == 9
        val angle = piece.angle.toFloat()
        val cAng = cos(angle)
        val sAng = sin(angle)
        val elev = piece.elevation.toFloat() + FLOOR_CLEARANCE_CM.toFloat()
        val px = piece.x.toFloat()
        val pz = piece.y.toFloat()

        val mapKd = resolveMapKd(file.parentFile, parsed)
        val hasAnyUv = parsed.texCoords.isNotEmpty() &&
            parsed.corners.any { it.uvIdx >= 0 }
        val groups = LinkedHashMap<String?, ArrayList<Corner>>()
        for (corner in parsed.corners) {
            val texKey = if (hasAnyUv) {
                val kd = corner.material?.let { mapKd[it] }
                if (kd != null && File(kd).isFile) kd else null
            } else {
                null
            }
            groups.getOrPut(texKey) { ArrayList() }.add(corner)
        }

        val out = ArrayList<MeshTri>(groups.size)
        for ((texPath, corners) in groups) {
            if (corners.size < 3) continue
            val outPos = FloatArray(corners.size * 3)
            val outNrm = FloatArray(corners.size * 3)
            val outUv = if (texPath != null) FloatArray(corners.size * 2) else null
            var o = 0
            var u = 0
            for (corner in corners) {
                val vi = corner.posIdx * 3
                if (vi + 2 >= parsed.positions.size) {
                    o += 3
                    u += 2
                    continue
                }
                // mirror → anchor → scale → modelRotation → yaw (iOS FurnitureTransform).
                var lx = (parsed.positions[vi] - centerX) * mx
                var ly = parsed.positions[vi + 1] - minY
                var lz = parsed.positions[vi + 2] - centerZ
                lx *= sx
                ly *= sy
                lz *= sz
                if (hasRot) {
                    val r = rot!!
                    val rx = (r[0] * lx + r[1] * ly + r[2] * lz).toFloat()
                    val ry = (r[3] * lx + r[4] * ly + r[5] * lz).toFloat()
                    val rz = (r[6] * lx + r[7] * ly + r[8] * lz).toFloat()
                    lx = rx
                    ly = ry
                    lz = rz
                }

                val wx = px + lx * cAng - lz * sAng
                val wy = elev + ly
                val wz = pz + lx * sAng + lz * cAng

                outPos[o] = wx * CM_TO_M
                outPos[o + 1] = wy * CM_TO_M
                outPos[o + 2] = wz * CM_TO_M

                if (corner.nrmIdx >= 0) {
                    val ni = corner.nrmIdx * 3
                    if (ni + 2 < parsed.normals.size) {
                        var nx = parsed.normals[ni] * mx
                        var ny = parsed.normals[ni + 1]
                        var nz = parsed.normals[ni + 2]
                        if (hasRot) {
                            val r = rot!!
                            val rx = (r[0] * nx + r[1] * ny + r[2] * nz).toFloat()
                            val ry = (r[3] * nx + r[4] * ny + r[5] * nz).toFloat()
                            val rz = (r[6] * nx + r[7] * ny + r[8] * nz).toFloat()
                            nx = rx
                            ny = ry
                            nz = rz
                        }
                        val nnx = nx * cAng - nz * sAng
                        val nnz = nx * sAng + nz * cAng
                        val len = sqrt(nnx * nnx + ny * ny + nnz * nnz).coerceAtLeast(1e-8f)
                        outNrm[o] = nnx / len
                        outNrm[o + 1] = ny / len
                        outNrm[o + 2] = nnz / len
                    }
                }

                if (outUv != null) {
                    if (corner.uvIdx >= 0) {
                        val ti = corner.uvIdx * 2
                        if (ti + 1 < parsed.texCoords.size) {
                            outUv[u] = parsed.texCoords[ti]
                            // OBJ V often bottom-origin; Filament UV0 is top-origin — flip V.
                            outUv[u + 1] = 1f - parsed.texCoords[ti + 1]
                        }
                    }
                    u += 2
                }
                o += 3
            }

            val hasNrm = corners.any { it.nrmIdx >= 0 }
            if (!hasNrm) {
                computeFlatNormals(outPos, outNrm, mirrored = piece.modelMirrored)
            } else if (piece.modelMirrored) {
                for (i in outNrm.indices) outNrm[i] = -outNrm[i]
            }

            out.add(
                MeshTri(
                    positions = outPos,
                    normals = outNrm,
                    colorArgb = colorArgb,
                    roughness = roughness,
                    uvs = outUv,
                    textureAssetPath = texPath?.let { "file:$it" },
                ),
            )
        }
        return out.takeIf { it.isNotEmpty() }
    }

    private data class Corner(
        val posIdx: Int,
        val uvIdx: Int,
        val nrmIdx: Int,
        val material: String?,
    )

    private data class ParsedObj(
        val positions: FloatArray,
        val texCoords: FloatArray,
        val normals: FloatArray,
        val corners: List<Corner>,
        val mtllibs: List<String>,
    )

    private fun parse(file: File): ParsedObj? {
        val positions = ArrayList<Float>(4096)
        val texCoords = ArrayList<Float>(2048)
        val normals = ArrayList<Float>(4096)
        val corners = ArrayList<Corner>(8192)
        val mtllibs = ArrayList<String>()
        var currentMtl: String? = null
        var triCount = 0
        try {
            file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (raw in lines) {
                    if (triCount >= MAX_TRIANGLES) break
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#")) continue
                    when {
                        line.startsWith("mtllib ") || line.startsWith("mtllib\t") -> {
                            val rest = line.substring(6).trim()
                            if (rest.isNotEmpty()) {
                                // A line may list several MTL files.
                                for (name in rest.split(Regex("\\s+"))) {
                                    if (name.isNotBlank()) mtllibs.add(name)
                                }
                            }
                        }
                        line.startsWith("usemtl ") || line.startsWith("usemtl\t") -> {
                            currentMtl = line.substring(6).trim().ifBlank { null }
                        }
                        line.startsWith("v ") -> {
                            val p = line.split(Regex("\\s+"))
                            if (p.size >= 4) {
                                positions.add(p[1].toFloatOrNaN())
                                positions.add(p[2].toFloatOrNaN())
                                positions.add(p[3].toFloatOrNaN())
                            }
                        }
                        line.startsWith("vt ") -> {
                            val p = line.split(Regex("\\s+"))
                            if (p.size >= 3) {
                                texCoords.add(p[1].toFloatOrNaN())
                                texCoords.add(p[2].toFloatOrNaN())
                            }
                        }
                        line.startsWith("vn ") -> {
                            val p = line.split(Regex("\\s+"))
                            if (p.size >= 4) {
                                normals.add(p[1].toFloatOrNaN())
                                normals.add(p[2].toFloatOrNaN())
                                normals.add(p[3].toFloatOrNaN())
                            }
                        }
                        line.startsWith("f ") -> {
                            val tokens = line.split(Regex("\\s+")).drop(1)
                            if (tokens.size < 3) continue
                            val verts = Array(tokens.size) { i ->
                                parseFaceToken(tokens[i], positions.size / 3, texCoords.size / 2, normals.size / 3)
                            }
                            // Fan triangulate.
                            for (i in 1 until verts.size - 1) {
                                if (triCount >= MAX_TRIANGLES) break
                                val a = verts[0] ?: continue
                                val b = verts[i] ?: continue
                                val c = verts[i + 1] ?: continue
                                corners.add(Corner(a[0], a[1], a[2], currentMtl))
                                corners.add(Corner(b[0], b[1], b[2], currentMtl))
                                corners.add(Corner(c[0], c[1], c[2], currentMtl))
                                triCount++
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            return null
        }
        if (positions.isEmpty() || corners.isEmpty()) return null
        val vertCount = positions.size / 3
        val safe = corners.filter { it.posIdx in 0 until vertCount }
        if (safe.size < 3) return null
        // Keep complete triangles only.
        val tris = ArrayList<Corner>(safe.size)
        var i = 0
        while (i + 2 < safe.size) {
            tris.add(safe[i])
            tris.add(safe[i + 1])
            tris.add(safe[i + 2])
            i += 3
        }
        if (tris.isEmpty()) return null
        return ParsedObj(
            positions.toFloatArray(),
            texCoords.toFloatArray(),
            normals.toFloatArray(),
            tris,
            mtllibs,
        )
    }

    /** Returns intArrayOf(posIdx, uvIdx, nrmIdx); uv/nrm = -1 when absent. */
    private fun parseFaceToken(
        token: String,
        posCount: Int,
        uvCount: Int,
        nrmCount: Int,
    ): IntArray? {
        val parts = token.split('/')
        val posRaw = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val posIdx = if (posRaw > 0) posRaw - 1 else posCount + posRaw
        val uvIdx = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }?.toIntOrNull()?.let { raw ->
            if (raw > 0) raw - 1 else uvCount + raw
        } ?: -1
        val nrmIdx = parts.getOrNull(2)?.takeIf { it.isNotEmpty() }?.toIntOrNull()?.let { raw ->
            if (raw > 0) raw - 1 else nrmCount + raw
        } ?: -1
        return intArrayOf(posIdx, uvIdx, nrmIdx)
    }

    /** material name → absolute map_Kd path (existing files only). */
    private fun resolveMapKd(objDir: File?, parsed: ParsedObj): Map<String, String> {
        if (objDir == null || parsed.mtllibs.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (lib in parsed.mtllibs) {
            val mtlFile = resolveSibling(objDir, lib) ?: continue
            parseMtlMapKd(mtlFile, out)
        }
        return out
    }

    private fun parseMtlMapKd(mtlFile: File, out: MutableMap<String, String>) {
        if (!mtlFile.isFile) return
        var current: String? = null
        try {
            mtlFile.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (raw in lines) {
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#")) continue
                    when {
                        line.startsWith("newmtl ") || line.startsWith("newmtl\t") -> {
                            current = line.substring(6).trim().ifBlank { null }
                        }
                        line.startsWith("map_Kd ") || line.startsWith("map_Kd\t") ||
                            line.startsWith("map_kd ") || line.startsWith("map_kd\t") -> {
                            val name = current ?: continue
                            val texName = lastMapKdToken(line.substringAfter(' ').trim()) ?: continue
                            val texFile = resolveSibling(mtlFile.parentFile ?: return@useLines, texName)
                            if (texFile != null && texFile.isFile) {
                                out[name] = texFile.absolutePath
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore unreadable MTL — furniture still renders with tint.
        }
    }

    /**
     * MTL allows options before the filename (`map_Kd -o 1 1 wood.jpg`).
     * Take the last non-option token.
     */
    internal fun lastMapKdToken(rest: String): String? {
        if (rest.isBlank()) return null
        val tokens = rest.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        // Walk options: -o/-s/-t take 1–3 floats; -mm takes 2; bare flags skip.
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (t.startsWith("-") && t.length <= 4 && !t.contains('.') && !t.contains('/') && !t.contains('\\')) {
                val skip = when (t) {
                    "-o", "-s", "-t" -> 3
                    "-mm" -> 2
                    "-blendu", "-blendv", "-cc", "-clamp" -> 1
                    else -> 1
                }
                i += 1 + skip
            } else {
                break
            }
        }
        // Prefer last path-like token if options left leftovers.
        for (j in tokens.lastIndex downTo i.coerceAtMost(tokens.lastIndex)) {
            val t = tokens[j]
            if (!t.startsWith("-")) return t.trim('"', '\'')
        }
        return tokens.lastOrNull()?.trim('"', '\'')
    }

    private fun resolveSibling(dir: File, relative: String): File? {
        val cleaned = relative.replace('\\', '/').trim().trim('"', '\'')
        if (cleaned.isEmpty() || cleaned.contains("..")) return null
        val direct = File(dir, cleaned)
        if (direct.isFile) return direct
        // Basename-only fallback (common when MTL paths are absolute-ish or nested).
        val base = cleaned.substringAfterLast('/')
        if (base.isNotEmpty() && base != cleaned) {
            val byName = File(dir, base)
            if (byName.isFile) return byName
        }
        return null
    }

    private fun String.toFloatOrNaN(): Float = toFloatOrNull() ?: Float.NaN

    private fun computeFlatNormals(pos: FloatArray, nrm: FloatArray, mirrored: Boolean) {
        val sign = if (mirrored) -1f else 1f
        var i = 0
        while (i + 8 < pos.size) {
            val ax = pos[i]
            val ay = pos[i + 1]
            val az = pos[i + 2]
            val bx = pos[i + 3]
            val by = pos[i + 4]
            val bz = pos[i + 5]
            val cx = pos[i + 6]
            val cy = pos[i + 7]
            val cz = pos[i + 8]
            var nx = (by - ay) * (cz - az) - (bz - az) * (cy - ay)
            var ny = (bz - az) * (cx - ax) - (bx - ax) * (cz - az)
            var nz = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
            nx *= sign
            ny *= sign
            nz *= sign
            val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-8f)
            nx /= len
            ny /= len
            nz /= len
            repeat(3) { k ->
                nrm[i + k * 3] = nx
                nrm[i + k * 3 + 1] = ny
                nrm[i + k * 3 + 2] = nz
            }
            i += 9
        }
    }
}
