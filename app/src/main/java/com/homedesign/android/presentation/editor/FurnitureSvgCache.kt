package com.homedesign.android.presentation.editor

import android.content.Context
import android.graphics.Matrix
import android.graphics.Path as AndroidPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.PathParser
import com.homedesign.android.domain.catalog.CatalogEntry
import com.homedesign.android.domain.geom.FurnitureSvgParse
import com.homedesign.android.domain.geom.FurnitureSymbolClassifier
import com.homedesign.android.domain.geom.FurnitureSymbols
import com.homedesign.android.domain.geom.ParsedFurnitureSvg
import com.homedesign.android.domain.model.HomePieceOfFurniture
import kotlin.math.round

/**
 * Loads catalog `icon` SVG assets once, parses path `d`s (web FurnitureSymbols
 * parity), and builds local-cm footprints for plan drawing. Falls back to
 * procedural path art when SVG is missing.
 */
class FurnitureSvgCache(private val context: Context) {
    private val parsed = HashMap<String, ParsedFurnitureSvg?>()
    private val localPaths = HashMap<String, List<AndroidPath>>()
    private val proceduralPaths = HashMap<String, List<AndroidPath>>()
    private val failed = HashSet<String>()

    fun artFor(icon: String?, widthCM: Double, depthCM: Double): List<AndroidPath>? {
        if (icon.isNullOrBlank()) return null
        if (widthCM <= 1.0 || depthCM <= 1.0) return null
        val asset = FurnitureSvgParse.assetPath(icon)
        if (asset in failed) return null
        val svg = parsed.getOrPut(asset) { loadParsed(asset) }
        if (svg == null) {
            failed.add(asset)
            return null
        }
        val key = "$asset|${round(widthCM * 10)}|${round(depthCM * 10)}"
        return localPaths.getOrPut(key) {
            buildLocalPaths(svg, widthCM, depthCM)
        }.takeIf { it.isNotEmpty() }
    }

    /** SVG icon art, else procedural strokes for classified kind. */
    fun artForPiece(
        piece: HomePieceOfFurniture,
        entry: CatalogEntry?,
        widthCM: Double,
        depthCM: Double,
    ): List<AndroidPath>? {
        artFor(entry?.icon, widthCM, depthCM)?.let { return it }
        if (widthCM <= 1.0 || depthCM <= 1.0) return null
        val kind = FurnitureSymbolClassifier.classify(piece, entry)
        val art = FurnitureSymbols.paths(kind, widthCM, depthCM)
        if (art.paths.isEmpty()) return null
        val key = "proc|$kind|${round(widthCM * 10)}|${round(depthCM * 10)}|${art.quarterTurn}"
        return proceduralPaths.getOrPut(key) {
            val matrix = Matrix()
            if (art.quarterTurn) matrix.postRotate(90f)
            art.paths.mapNotNull { stroke ->
                val src = runCatching { PathParser.createPathFromPathData(stroke.d) }.getOrNull()
                    ?: return@mapNotNull null
                AndroidPath(src).also { if (!matrix.isIdentity) it.transform(matrix) }
            }
        }.takeIf { it.isNotEmpty() }
    }

    private fun loadParsed(asset: String): ParsedFurnitureSvg? {
        return runCatching {
            context.assets.open(asset).bufferedReader().use { it.readText() }
        }.mapCatching { FurnitureSvgParse.parse(it) }.getOrNull()
    }

    private fun buildLocalPaths(
        svg: ParsedFurnitureSvg,
        widthCM: Double,
        depthCM: Double,
    ): List<AndroidPath> {
        val sx = (widthCM / svg.width).toFloat()
        val sy = (depthCM / svg.height).toFloat()
        val matrix = Matrix().apply {
            // viewBox → local cm, front +y / centred (web translate(-w/2,-d/2) scale)
            postScale(sx, sy)
            postTranslate((-widthCM / 2.0).toFloat(), (-depthCM / 2.0).toFloat())
        }
        return svg.paths.mapNotNull { d ->
            val src = runCatching { PathParser.createPathFromPathData(d) }.getOrNull()
                ?: return@mapNotNull null
            AndroidPath(src).also { it.transform(matrix) }
        }
    }
}

@Composable
fun rememberFurnitureSvgCache(): FurnitureSvgCache {
    val context = LocalContext.current.applicationContext
    return remember(context) { FurnitureSvgCache(context) }
}
