package com.homedesign.android.domain.model

import kotlin.math.round

object UnitFormat {
    const val CM_PER_INCH = 2.54
    const val CM_PER_FOOT = 30.48
    private const val M2_TO_FT2 = 10.7639

    private fun formatInches(whole: Int, hasHalf: Boolean): String =
        when {
            hasHalf && whole == 0 -> "½″"
            hasHalf -> "${whole}½″"
            else -> "${whole}″"
        }

    private fun formatImperialLength(cm: Double): String {
        var halfInches = round((cm / CM_PER_INCH) * 2).toInt()
        var feet = halfInches / 24
        var halves = halfInches - feet * 24
        if (halves == 24) {
            feet += 1
            halves = 0
        }
        val wholeInches = halves / 2
        val hasHalf = halves % 2 == 1
        if (feet == 0) return formatInches(wholeInches, hasHalf)
        if (wholeInches == 0 && !hasHalf) return "${feet}′"
        return "${feet}′ ${formatInches(wholeInches, hasHalf)}"
    }

    fun length(cm: Double, system: UnitSystem): String = when (system) {
        UnitSystem.Millimetre -> "${round(cm * 10).toInt()} mm"
        UnitSystem.Metric -> if (cm < 100) {
            "${round(cm).toInt()} cm"
        } else {
            String.format("%.2f m", cm / 100.0)
        }
        UnitSystem.Imperial -> formatImperialLength(cm)
    }

    /** Editable numeric value in [system] (mm int, cm int, ft 2dp). */
    fun toUnit(cm: Double, system: UnitSystem): Double = when (system) {
        UnitSystem.Millimetre -> round(cm * 10)
        UnitSystem.Metric -> round(cm)
        UnitSystem.Imperial -> round((cm / CM_PER_FOOT) * 100) / 100.0
    }

    /** Convert a sheet-field number in [system] back to centimetres. */
    fun fromUnit(value: Double, system: UnitSystem): Double = when (system) {
        UnitSystem.Millimetre -> value / 10.0
        UnitSystem.Metric -> value
        UnitSystem.Imperial -> value * CM_PER_FOOT
    }

    fun editPrecision(system: UnitSystem): Int =
        if (system == UnitSystem.Imperial) 2 else 0

    /** +/- increment in the display unit (mm 10, cm 1, ft 0.1). */
    fun editStep(system: UnitSystem): Double = when (system) {
        UnitSystem.Millimetre -> 10.0
        UnitSystem.Metric -> 1.0
        UnitSystem.Imperial -> 0.1
    }

    fun suffix(system: UnitSystem): String = when (system) {
        UnitSystem.Millimetre -> "mm"
        UnitSystem.Metric -> "cm"
        UnitSystem.Imperial -> "ft"
    }

    fun parse(text: String, system: UnitSystem): Double? = parseLength(text, system)

    fun area(m2: Double, system: UnitSystem): String =
        if (system == UnitSystem.Imperial) {
            "${round(m2 * M2_TO_FT2).toInt()} ft²"
        } else {
            String.format("%.2f m²", m2)
        }

    private val SUFFIX_TABLE = listOf(
        "mm" to 0.1,
        "cm" to 1.0,
        "ft" to CM_PER_FOOT,
        "in" to CM_PER_INCH,
        "'" to CM_PER_FOOT,
        "\"" to CM_PER_INCH,
        "m" to 100.0,
    )

    private fun parseNumber(s: String): Double? {
        if (s.isEmpty()) return null
        var sawDot = false
        for (c in s) {
            when {
                c == '.' -> if (sawDot) return null else sawDot = true
                c !in '0'..'9' -> return null
            }
        }
        return s.toDoubleOrNull()
    }

    private fun splitOn(str: String, marker: String): Pair<Double, String>? {
        val idx = str.indexOf(marker)
        if (idx < 0) return null
        val n = parseNumber(str.substring(0, idx)) ?: return null
        return n to str.substring(idx + marker.length)
    }

    private fun parseFeetInches(s: String): Double? {
        val feetMarker = when {
            s.contains("ft") -> "ft"
            s.contains("'") -> "'"
            else -> return null
        }
        val feet = splitOn(s, feetMarker) ?: return null
        if (feet.second.isEmpty()) return feet.first * CM_PER_FOOT
        val inMarker = when {
            feet.second.contains("in") -> "in"
            feet.second.contains("\"") -> "\""
            else -> return null
        }
        val inches = splitOn(feet.second, inMarker) ?: return null
        if (inches.second.isNotEmpty()) return null
        return feet.first * CM_PER_FOOT + inches.first * CM_PER_INCH
    }

    private fun parseLength(text: String, system: UnitSystem): Double? {
        var s = text.lowercase()
            .replace('′', '\'')
            .replace('″', '"')
            .replace(Regex("[\\s\\u00a0\\u2009]+"), "")
        if (s.isEmpty()) return null
        parseFeetInches(s)?.let { return it }
        for ((suffix, cmPer) in SUFFIX_TABLE) {
            if (s.endsWith(suffix)) {
                val n = parseNumber(s.dropLast(suffix.length)) ?: return null
                return n * cmPer
            }
        }
        val n = parseNumber(s) ?: return null
        return when (system) {
            UnitSystem.Millimetre -> n / 10.0
            UnitSystem.Metric -> n
            UnitSystem.Imperial -> n * CM_PER_INCH
        }
    }
}
