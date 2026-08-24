package com.homedesign.android.domain.geom

/**
 * Parses plan-symbol SVG assets (viewBox + `<path d>`), matching web
 * `FurnitureSymbols.parseSvg`. Pure — no Android AssetManager.
 */
data class ParsedFurnitureSvg(
    val width: Double,
    val height: Double,
    val paths: List<String>,
)

object FurnitureSvgParse {
    private val viewBoxRe = Regex("""viewBox\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val pathDRe = Regex("""<path\b[^>]*\bd\s*=\s*["']([^"']+)["']""", setOf(RegexOption.IGNORE_CASE))

    fun parse(raw: String): ParsedFurnitureSvg? {
        if (raw.isBlank()) return null
        val vb = viewBoxRe.find(raw)?.groupValues?.getOrNull(1)
        val parts = (vb ?: "0 0 1 1").trim().split(Regex("""[\s,]+""")).mapNotNull { it.toDoubleOrNull() }
        val width = parts.getOrNull(2)?.takeIf { it > 0 } ?: 1.0
        val height = parts.getOrNull(3)?.takeIf { it > 0 } ?: 1.0
        val paths = pathDRe.findAll(raw).mapNotNull { m ->
            m.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
        }.toList()
        if (paths.isEmpty()) return null
        return ParsedFurnitureSvg(width = width, height = height, paths = paths)
    }

    /** Normalise catalog `icon` (`svg/bed.svg`, `/svg/bed.svg`, `bed.svg`) to assets-relative. */
    fun assetPath(icon: String): String {
        val trimmed = icon.trim().removePrefix("/")
        return when {
            trimmed.startsWith("svg/") -> trimmed
            trimmed.endsWith(".svg", ignoreCase = true) -> "svg/$trimmed"
            else -> "svg/$trimmed.svg"
        }
    }
}
