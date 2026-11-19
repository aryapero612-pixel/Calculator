package com.smartcalc.core

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs

object NumberFormatter {
    /** Formats a double for display: 12 significant digits, no trailing zeros, scientific for very large/small. */
    fun format(value: Double, grouping: Boolean = false): String {
        if (value.isNaN()) return "Error"
        if (value.isInfinite()) return if (value > 0) "∞" else "-∞"
        if (value == 0.0) return "0"
        val a = abs(value)
        if (a >= 1e15 || a < 1e-9) {
            val bd = BigDecimal(value).round(MathContext(10, RoundingMode.HALF_UP))
            val parts = String.format(java.util.Locale.US, "%.9E", bd.toDouble()).split("E")
            val mantissa = parts[0].trimEnd('0').trimEnd('.')
            val exp = parts[1].toInt()
            return "${mantissa}E$exp"
        }
        val bd = BigDecimal(value).round(MathContext(12, RoundingMode.HALF_UP)).stripTrailingZeros()
        val plain = bd.toPlainString()
        return if (grouping) group(plain) else plain
    }

    /** Indian/International-neutral grouping with commas every 3 digits. */
    private fun group(plain: String): String {
        val neg = plain.startsWith("-")
        val body = plain.removePrefix("-")
        val intPart = body.substringBefore('.')
        val frac = body.substringAfter('.', "")
        val grouped = intPart.reversed().chunked(3).joinToString(",").reversed()
        return (if (neg) "-" else "") + grouped + if (frac.isNotEmpty()) ".$frac" else ""
    }

    fun money(value: Double): String = format(BigDecimal(value).setScale(2, RoundingMode.HALF_UP).toDouble(), grouping = true)
}
