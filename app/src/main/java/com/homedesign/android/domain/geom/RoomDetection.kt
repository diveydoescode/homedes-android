package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.Point
import com.homedesign.android.domain.model.Room
import com.homedesign.android.domain.model.Wall
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class FaceRing(
    val polygon: List<Vec2>,
    val outHalfThickness: List<Double>,
)

private data class HalfEdge(
    val wallIndex: Int,
    val forward: Boolean,
    val source: Int,
    val target: Int,
    val angle: Double,
    val twin: Int,
)

private data class ClusterResult(
    val wallClusterIDs: List<Pair<Int, Int>>,
    val positions: List<Vec2>,
)

private data class TraceResult(
    val faces: List<List<Int>>,
    val halfEdges: List<HalfEdge>,
    val clusters: ClusterResult,
    val walls: List<Wall>,
)

private fun signedArea(face: List<Int>, halfEdges: List<HalfEdge>, positions: List<Vec2>): Double {
    if (face.size < 3) return 0.0
    var sum = 0.0
    for (i in face.indices) {
        val curP = positions[halfEdges[face[i]].source]
        val nextP = positions[halfEdges[face[(i + 1) % face.size]].source]
        sum += (nextP.x - curP.x) * (nextP.y + curP.y)
    }
    return -sum / 2.0
}

private fun polygonSignedArea(poly: List<Vec2>): Double {
    if (poly.size < 3) return 0.0
    var sum = 0.0
    for (i in poly.indices) {
        val a = poly[i]
        val b = poly[(i + 1) % poly.size]
        sum += (b.x - a.x) * (b.y + a.y)
    }
    return -sum / 2.0
}

private fun clusterEndpoints(walls: List<Wall>, epsilonCM: Double): ClusterResult {
    val allEndpoints = mutableListOf<Vec2>()
    for (w in walls) {
        allEndpoints.add(vec(w.startX, w.startY))
        allEndpoints.add(vec(w.endX, w.endY))
    }
    val parent = IntArray(allEndpoints.size) { it }

    fun find(x: Int): Int {
        var root = x
        while (parent[root] != root) root = parent[root]
        var cur = x
        while (parent[cur] != root) {
            val next = parent[cur]
            parent[cur] = root
            cur = next
        }
        return root
    }

    fun union(a: Int, b: Int) {
        val ra = find(a)
        val rb = find(b)
        if (ra != rb) parent[max(ra, rb)] = min(ra, rb)
    }

    val eps2 = epsilonCM * epsilonCM
    for (i in allEndpoints.indices) {
        for (j in i + 1 until allEndpoints.size) {
            if (distSq(allEndpoints[i], allEndpoints[j]) <= eps2) union(i, j)
        }
    }

    val indicesByID = mutableMapOf<String, MutableList<Int>>()
    for (i in walls.indices) {
        indicesByID.getOrPut(walls[i].id) { mutableListOf() }.add(i)
    }
    for (i in walls.indices) {
        val w = walls[i]
        val refs = listOf(i * 2 to w.atStart, i * 2 + 1 to w.atEnd)
        for ((endpointIdx, ref) in refs) {
            if (ref == null) continue
            val targets = indicesByID[ref] ?: continue
            val p = allEndpoints[endpointIdx]
            var best = -1
            var bestD = Double.POSITIVE_INFINITY
            for (j in targets) {
                if (j == i) continue
                for (cand in listOf(j * 2, j * 2 + 1)) {
                    val d = distSq(allEndpoints[cand], p)
                    if (d < bestD) {
                        bestD = d
                        best = cand
                    }
                }
            }
            if (best >= 0) union(endpointIdx, best)
        }
    }

    if (epsilonCM >= 1.0) {
        val memberCount = mutableMapOf<Int, Int>()
        for (i in allEndpoints.indices) {
            val root = find(i)
            memberCount[root] = (memberCount[root] ?: 0) + 1
        }
        val tips = mutableListOf<Int>()
        for (i in allEndpoints.indices) {
            if (memberCount[find(i)] == 1) tips.add(i)
        }
        for (ti in tips.indices) {
            val a = tips[ti]
            for (tj in ti + 1 until tips.size) {
                val b = tips[tj]
                if (find(a) == find(b)) continue
                val limit = max(walls[a / 2].thickness, walls[b / 2].thickness)
                if (distSq(allEndpoints[a], allEndpoints[b]) <= limit * limit) {
                    union(a, b)
                }
            }
        }
    }

    val idOfRoot = mutableMapOf<Int, Int>()
    val clusterOf = IntArray(allEndpoints.size) { -1 }
    val sums = mutableListOf<Vec2>()
    val counts = mutableListOf<Int>()
    for (i in allEndpoints.indices) {
        val root = find(i)
        var id = idOfRoot[root]
        if (id == null) {
            id = sums.size
            idOfRoot[root] = id
            sums.add(vec(0.0, 0.0))
            counts.add(0)
        }
        clusterOf[i] = id
        sums[id] = add(sums[id], allEndpoints[i])
        counts[id] = counts[id] + 1
    }
    val clusterPositions = sums.mapIndexed { k, s -> scale(s, 1.0 / counts[k]) }
    val wallClusterIDs = mutableListOf<Pair<Int, Int>>()
    for (i in walls.indices) {
        wallClusterIDs.add(clusterOf[i * 2] to clusterOf[i * 2 + 1])
    }
    return ClusterResult(wallClusterIDs, clusterPositions)
}

private fun splitAtCrossings(walls: List<Wall>, epsilonCM: Double): List<Wall> {
    val cutsByWall = mutableMapOf<Int, MutableList<Pair<Double, Vec2>>>()
    for (i in walls.indices) {
        val wi = walls[i]
        val a0 = vec(wi.startX, wi.startY)
        val a1 = vec(wi.endX, wi.endY)
        val r = sub(a1, a0)
        val rLen = length(r)
        if (rLen <= 1e-9) continue
        for (j in i + 1 until walls.size) {
            val wj = walls[j]
            val b0 = vec(wj.startX, wj.startY)
            val b1 = vec(wj.endX, wj.endY)
            val sDir = sub(b1, b0)
            val sLen = length(sDir)
            if (sLen <= 1e-9) continue
            val denom = r.x * sDir.y - r.y * sDir.x
            if (abs(denom) <= 1e-9) continue
            val diff = sub(b0, a0)
            val t = (diff.x * sDir.y - diff.y * sDir.x) / denom
            val u = (diff.x * r.y - diff.y * r.x) / denom
            val endZone = max(epsilonCM, wi.thickness / 2.0 + wj.thickness / 2.0)
            val si = t * rLen
            val sj = u * sLen
            if (!(si > endZone && si < rLen - endZone && sj > endZone && sj < sLen - endZone)) {
                continue
            }
            val p = add(a0, scale(r, t))
            cutsByWall.getOrPut(i) { mutableListOf() }.add(si to p)
            cutsByWall.getOrPut(j) { mutableListOf() }.add(sj to p)
        }
    }
    if (cutsByWall.isEmpty()) return walls

    val result = mutableListOf<Wall>()
    for (i in walls.indices) {
        val cuts = cutsByWall[i]
        if (cuts.isNullOrEmpty()) {
            result.add(walls[i])
            continue
        }
        cuts.sortBy { it.first }
        val distinct = mutableListOf<Pair<Double, Vec2>>()
        for (c in cuts) {
            val last = distinct.lastOrNull()
            if (last != null && abs(c.first - last.first) <= epsilonCM) continue
            distinct.add(c)
        }
        val host = walls[i]
        var cursor = vec(host.startX, host.startY)
        var atStart = host.atStart
        for (cut in distinct) {
            result.add(
                host.copy(
                    startX = cursor.x,
                    startY = cursor.y,
                    endX = cut.second.x,
                    endY = cut.second.y,
                    atStart = atStart,
                    atEnd = null,
                ),
            )
            cursor = cut.second
            atStart = null
        }
        result.add(host.copy(startX = cursor.x, startY = cursor.y, atStart = null))
    }
    return result
}

private fun subArcSegment(
    host: Wall,
    start: Vec2,
    end: Vec2,
    t0: Double,
    t1: Double,
    atStart: String?,
    atEnd: String?,
): Wall {
    var seg = host.copy(
        startX = start.x,
        startY = start.y,
        endX = end.x,
        endY = end.y,
        atStart = atStart,
        atEnd = atEnd,
        curveProfile = null,
    )
    seg = if (host.curveProfile == null) {
        seg.copy(arcExtent = (host.arcExtent ?: 0.0) * (t1 - t0))
    } else {
        val mid = ArcWallGeometry.pointAt(host, (t0 + t1) / 2.0)
        val fitted = ArcWallGeometry.extent(start, end, mid)
        if (fitted == 0.0) seg.copy(arcExtent = null) else seg.copy(arcExtent = fitted)
    }
    return seg
}

private fun splitCurvedHostAtTJunctions(
    host: Wall,
    hostIndex: Int,
    walls: List<Wall>,
    allEndpoints: List<Vec2>,
    epsilonCM: Double,
): List<Wall> {
    val arcLen = ArcWallGeometry.arcLength(host)
    if (arcLen <= 1e-9) return listOf(host)
    val cuts = mutableListOf<Pair<Double, Vec2>>()
    for (j in allEndpoints.indices) {
        if (j / 2 == hostIndex) continue
        val p = allEndpoints[j]
        val offTol = max(epsilonCM, host.thickness / 2.0)
        val endZone = max(epsilonCM, host.thickness / 2.0 + walls[j / 2].thickness / 2.0)
        val hit = ArcWallGeometry.closestPoint(p, host)
        if (hit.distance > offTol) continue
        if (!(hit.t * arcLen > endZone && (1 - hit.t) * arcLen > endZone)) continue
        cuts.add(hit.t to p)
    }
    if (cuts.isEmpty()) return listOf(host)
    cuts.sortBy { it.first }
    val distinct = mutableListOf<Pair<Double, Vec2>>()
    for (c in cuts) {
        val last = distinct.lastOrNull()
        if (last != null && (c.first - last.first) * arcLen <= epsilonCM) continue
        distinct.add(c)
    }
    val pieces = mutableListOf<Wall>()
    var cursor = vec(host.startX, host.startY)
    var prevT = 0.0
    var atStart = host.atStart
    for (cut in distinct) {
        pieces.add(subArcSegment(host, cursor, cut.second, prevT, cut.first, atStart, null))
        cursor = cut.second
        prevT = cut.first
        atStart = null
    }
    pieces.add(
        subArcSegment(
            host,
            cursor,
            vec(host.endX, host.endY),
            prevT,
            1.0,
            null,
            host.atEnd,
        ),
    )
    return pieces
}

private fun splitAtTJunctions(walls: List<Wall>, epsilonCM: Double): List<Wall> {
    val allEndpoints = mutableListOf<Vec2>()
    for (w in walls) {
        allEndpoints.add(vec(w.startX, w.startY))
        allEndpoints.add(vec(w.endX, w.endY))
    }
    val result = mutableListOf<Wall>()
    for (i in walls.indices) {
        val host = walls[i]
        if (ArcWallGeometry.isCurved(host)) {
            result.addAll(splitCurvedHostAtTJunctions(host, i, walls, allEndpoints, epsilonCM))
            continue
        }
        val a = vec(host.startX, host.startY)
        val b = vec(host.endX, host.endY)
        val ab = sub(b, a)
        val len = length(ab)
        if (len <= 1e-9) {
            result.add(host)
            continue
        }
        val dir = scale(ab, 1.0 / len)
        val tolerance = max(epsilonCM, host.thickness / 2.0)
        val cuts = mutableListOf<Pair<Double, Vec2>>()
        for (j in allEndpoints.indices) {
            if (j / 2 == i) continue
            val p = allEndpoints[j]
            val endZone = max(epsilonCM, host.thickness / 2.0 + walls[j / 2].thickness / 2.0)
            val s = dot(sub(p, a), dir)
            if (!(s > endZone && s < len - endZone)) continue
            val proj = add(a, scale(dir, s))
            val offLine = length(sub(p, proj))
            if (offLine <= tolerance) cuts.add(s to p)
        }
        if (cuts.isEmpty()) {
            result.add(host)
            continue
        }
        cuts.sortBy { it.first }
        val distinct = mutableListOf<Pair<Double, Vec2>>()
        for (c in cuts) {
            val last = distinct.lastOrNull()
            if (last != null && abs(c.first - last.first) <= epsilonCM) continue
            distinct.add(c)
        }
        var cursor = a
        var atStart = host.atStart
        for (cut in distinct) {
            result.add(
                host.copy(
                    startX = cursor.x,
                    startY = cursor.y,
                    endX = cut.second.x,
                    endY = cut.second.y,
                    atStart = atStart,
                    atEnd = null,
                ),
            )
            cursor = cut.second
            atStart = null
        }
        result.add(host.copy(startX = cursor.x, startY = cursor.y, atStart = null))
    }
    return result
}

private fun traceAllFaces(walls: List<Wall>, epsilonCM: Double): TraceResult? {
    val degenerateFiltered = walls.filter { w ->
        val dx = w.endX - w.startX
        val dy = w.endY - w.startY
        dx * dx + dy * dy > epsilonCM * epsilonCM
    }
    if (degenerateFiltered.isEmpty()) return null

    val crossSplit = splitAtCrossings(degenerateFiltered, epsilonCM)
    val filteredWalls = splitAtTJunctions(crossSplit, epsilonCM)
    val clusters = clusterEndpoints(filteredWalls, epsilonCM)

    val halfEdges = mutableListOf<HalfEdge>()
    for (wallIdx in filteredWalls.indices) {
        val (srcID, dstID) = clusters.wallClusterIDs[wallIdx]
        val srcP = clusters.positions[srcID]
        val dstP = clusters.positions[dstID]
        val i = halfEdges.size
        halfEdges.add(
            HalfEdge(
                wallIndex = wallIdx,
                forward = true,
                source = srcID,
                target = dstID,
                angle = atan2(dstP.y - srcP.y, dstP.x - srcP.x),
                twin = i + 1,
            ),
        )
        halfEdges.add(
            HalfEdge(
                wallIndex = wallIdx,
                forward = false,
                source = dstID,
                target = srcID,
                angle = atan2(srcP.y - dstP.y, srcP.x - dstP.x),
                twin = i,
            ),
        )
    }

    val outgoingByCluster = mutableMapOf<Int, MutableList<Int>>()
    for (idx in halfEdges.indices) {
        val he = halfEdges[idx]
        outgoingByCluster.getOrPut(he.source) { mutableListOf() }.add(idx)
    }
    for ((k, v) in outgoingByCluster) {
        outgoingByCluster[k] = v.sortedBy { halfEdges[it].angle }.toMutableList()
    }

    val visited = BooleanArray(halfEdges.size)
    val faces = mutableListOf<List<Int>>()
    for (startIdx in halfEdges.indices) {
        if (visited[startIdx]) continue
        val face = mutableListOf<Int>()
        var cur = startIdx
        while (!visited[cur]) {
            visited[cur] = true
            face.add(cur)
            val curHE = halfEdges[cur]
            val twinIdx = curHE.twin
            val twinHE = halfEdges[twinIdx]
            val sorted = outgoingByCluster[twinHE.source] ?: break
            val twinPos = sorted.indexOf(twinIdx)
            if (twinPos < 0) break
            val nextPos = (twinPos - 1 + sorted.size) % sorted.size
            cur = sorted[nextPos]
        }
        if (face.isNotEmpty()) faces.add(face)
    }
    return TraceResult(faces, halfEdges, clusters, filteredWalls)
}

private fun polygonCentroidVec(polygon: List<Vec2>): Vec2 {
    if (polygon.isEmpty()) return vec(0.0, 0.0)
    var twiceArea = 0.0
    var wx = 0.0
    var wy = 0.0
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[(i + 1) % polygon.size]
        val cross = a.x * b.y - b.x * a.y
        twiceArea += cross
        wx += (a.x + b.x) * cross
        wy += (a.y + b.y) * cross
    }
    if (abs(twiceArea) < 1e-9) {
        var sx = 0.0
        var sy = 0.0
        for (p in polygon) {
            sx += p.x
            sy += p.y
        }
        return vec(sx / polygon.size, sy / polygon.size)
    }
    return vec(wx / (3.0 * twiceArea), wy / (3.0 * twiceArea))
}

private fun polygonContains(polygon: List<Vec2>, point: Vec2): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[j]
        if ((a.y > point.y) != (b.y > point.y)) {
            val t = (point.y - a.y) / (b.y - a.y)
            if (point.x < a.x + t * (b.x - a.x)) inside = !inside
        }
        j = i
    }
    return inside
}

object RoomDetection {
    fun detectFaceRings(
        walls: List<Wall>,
        epsilonCM: Double = roomDetectEpsilonCM,
        areaEpsilon: Double = roomAreaEpsilonCM2,
    ): List<FaceRing> {
        val traced = traceAllFaces(walls, epsilonCM) ?: return emptyList()
        val result = mutableListOf<FaceRing>()
        for (face in traced.faces) {
            if (face.size < 3) continue
            val polygon = mutableListOf<Vec2>()
            val outHalfThickness = mutableListOf<Double>()
            for (heIdx in face) {
                val he = traced.halfEdges[heIdx]
                val wall = if (he.wallIndex >= 0) traced.walls[he.wallIndex] else null
                val half = wall?.thickness?.div(2.0) ?: roomInsetDefaultCM
                polygon.add(traced.clusters.positions[he.source])
                outHalfThickness.add(half)
                if (wall != null && ArcWallGeometry.isCurved(wall)) {
                    var line = ArcWallGeometry.centerline(wall)
                    if (!he.forward) line = line.asReversed()
                    for (i in 1 until line.lastIndex) {
                        polygon.add(line[i])
                        outHalfThickness.add(half)
                    }
                }
            }
            val area = polygonSignedArea(polygon)
            if (area > areaEpsilon) result.add(FaceRing(polygon, outHalfThickness))
        }
        return result
    }

    fun detectFaces(
        walls: List<Wall>,
        epsilonCM: Double = roomDetectEpsilonCM,
        areaEpsilon: Double = roomAreaEpsilonCM2,
    ): List<List<Vec2>> = detectFaceRings(walls, epsilonCM, areaEpsilon).map { it.polygon }

    data class ExteriorEdge(val wallID: String, val start: Vec2, val end: Vec2)

    fun exteriorWallEdges(
        walls: List<Wall>,
        epsilonCM: Double = roomDetectEpsilonCM,
        areaEpsilon: Double = roomAreaEpsilonCM2,
    ): List<ExteriorEdge> {
        val traced = traceAllFaces(walls, epsilonCM) ?: return emptyList()
        var bestFace: List<Int>? = null
        var bestArea = -areaEpsilon
        for (face in traced.faces) {
            if (face.size < 3) continue
            val area = signedArea(face, traced.halfEdges, traced.clusters.positions)
            if (area < bestArea) {
                bestArea = area
                bestFace = face
            }
        }
        val face = bestFace ?: return emptyList()
        val out = mutableListOf<ExteriorEdge>()
        for (i in face.indices.reversed()) {
            val he = traced.halfEdges[face[i]]
            if (he.wallIndex < 0) continue
            out.add(
                ExteriorEdge(
                    wallID = traced.walls[he.wallIndex].id,
                    start = traced.clusters.positions[he.target],
                    end = traced.clusters.positions[he.source],
                ),
            )
        }
        return out
    }

    fun insetFacePolygon(
        face: List<Vec2>,
        halfThicknessCM: Any,
        miterLimit: Double = 4.0,
    ): List<Vec2> {
        val n = face.size
        if (n < 3) return face.toList()
        val result = mutableListOf<Vec2>()
        val uniform = halfThicknessCM as? Double
        @Suppress("UNCHECKED_CAST")
        val perEdge = if (halfThicknessCM is List<*>) halfThicknessCM as List<Double> else null
        for (i in 0 until n) {
            val prev = face[(i - 1 + n) % n]
            val curr = face[i]
            val next = face[(i + 1) % n]
            val inDir = normalize(sub(curr, prev))
            val outDir = normalize(sub(next, curr))
            val inPerp = vec(-inDir.y, inDir.x)
            val outPerp = vec(-outDir.y, outDir.x)

            if (uniform != null) {
                val bisectorSum = add(inPerp, outPerp)
                val bisectorLen = length(bisectorSum)
                if (bisectorLen < 1e-9) {
                    result.add(add(curr, scale(inPerp, uniform)))
                    continue
                }
                val bisector = scale(bisectorSum, 1.0 / bisectorLen)
                val sinHalfAngle = dot(bisector, inPerp)
                val safeSin = max(sinHalfAngle, 1.0 / miterLimit)
                val miter = uniform / safeSin
                result.add(add(curr, scale(bisector, miter)))
                continue
            }

            val hIn = perEdge?.get((i - 1 + n) % n) ?: roomInsetDefaultCM
            val hOut = perEdge?.get(i) ?: roomInsetDefaultCM
            val det = inPerp.x * outPerp.y - inPerp.y * outPerp.x
            if (abs(det) < 1e-9) {
                result.add(add(curr, scale(inPerp, max(hIn, hOut))))
                continue
            }
            var dx = (hIn * outPerp.y - hOut * inPerp.y) / det
            var dy = (inPerp.x * hOut - outPerp.x * hIn) / det
            val limit = max(hIn, hOut) * miterLimit
            val reach = hypot(dx, dy)
            if (reach > limit && reach > 1e-9) {
                val k = limit / reach
                dx *= k
                dy *= k
            }
            result.add(vec(curr.x + dx, curr.y + dy))
        }
        return result
    }

    fun constructRoom(polygon: List<Vec2>, level: String?): Room = Room(
        id = UUID.randomUUID().toString().lowercase(),
        points = polygon.map { Point(it.x, it.y) },
        name = null,
        areaVisible = true,
        floorVisible = true,
        ceilingVisible = true,
        ceilingFlat = false,
        level = level,
        autoDetected = true,
    )

    fun reconcileRooms(
        walls: List<Wall>,
        existing: List<Room>,
        level: String? = null,
        halfThicknessCM: Double? = null,
        epsilonCM: Double = roomDetectEpsilonCM,
        miterLimit: Double = 4.0,
        centroidMatchCM: Double = roomCentroidMatchCM,
    ): List<Room> {
        val scopedWalls = walls.filter { it.level == level }
        val levelScoped = existing.filter { it.level == level }
        val otherLevels = existing.filter { it.level != level }
        val nonAutoRooms = levelScoped.filter { !it.autoDetected }
        val autoRoomsAvailable = levelScoped.filter { it.autoDetected }.toMutableList()

        val detectionWalls = WallTJunction.centrelineProjected(scopedWalls)
        val rings = detectFaceRings(detectionWalls, epsilonCM)
        val detectedPolygons = rings.map { ring ->
            if (halfThicknessCM != null) {
                insetFacePolygon(ring.polygon, halfThicknessCM, miterLimit)
            } else {
                insetFacePolygon(ring.polygon, ring.outHalfThickness, miterLimit)
            }
        }

        val matchedAutoRooms = mutableListOf<Room>()
        val unmatchedDetected = mutableListOf<List<Vec2>>()
        for (polygon in detectedPolygons) {
            val centroidP = polygonCentroidVec(polygon)
            var bestIdx: Int? = null
            var bestDist = centroidMatchCM
            for (i in autoRoomsAvailable.indices) {
                val roomCentroid = polygonCentroidVec(
                    autoRoomsAvailable[i].points.map { vec(it.x, it.y) },
                )
                val dx = centroidP.x - roomCentroid.x
                val dy = centroidP.y - roomCentroid.y
                val d = hypot(dx, dy)
                if (d < bestDist) {
                    bestDist = d
                    bestIdx = i
                }
            }
            if (bestIdx != null) {
                val updated = autoRoomsAvailable[bestIdx].copy(
                    points = polygon.map { Point(it.x, it.y) },
                )
                matchedAutoRooms.add(updated)
                autoRoomsAvailable.removeAt(bestIdx)
            } else {
                unmatchedDetected.add(polygon)
            }
        }

        val filteredUnmatched = unmatchedDetected.filter { polygon ->
            val pc = polygonCentroidVec(polygon)
            for (room in nonAutoRooms) {
                val roomPoly = room.points.map { vec(it.x, it.y) }
                val rc = polygonCentroidVec(roomPoly)
                val dx = pc.x - rc.x
                val dy = pc.y - rc.y
                if (hypot(dx, dy) < centroidMatchCM) return@filter false
                if (polygonContains(roomPoly, pc) || polygonContains(polygon, rc)) {
                    return@filter false
                }
            }
            true
        }

        val newAutoRooms = filteredUnmatched.map { constructRoom(it, level) }
        return nonAutoRooms + matchedAutoRooms + newAutoRooms + otherLevels
    }
}
