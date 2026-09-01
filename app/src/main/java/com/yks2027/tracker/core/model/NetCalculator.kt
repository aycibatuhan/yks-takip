package com.yks2027.tracker.core.model

import kotlin.math.abs

/**
 * PRD §3.2. All net math is integer quarter-units: netQuarters = 4·doğru − yanlış.
 * Exact, sortable, no floats anywhere until a chart's final render step.
 * Negative nets are legitimate (ÖSYM convention) and must flow through unclamped.
 */
object NetCalculator {

    fun netQuarters(correct: Int, wrong: Int): Int = 4 * correct - wrong

    fun blank(questionCount: Int, correct: Int, wrong: Int): Int =
        questionCount - correct - wrong

    fun isValid(questionCount: Int, correct: Int, wrong: Int): Boolean =
        correct >= 0 && wrong >= 0 && correct + wrong <= questionCount

    /** Formats quarter-units with a Turkish decimal comma: 114 → "28,50", -3 → "-0,75". */
    fun format(quarters: Int): String {
        val sign = if (quarters < 0) "-" else ""
        val q = abs(quarters)
        val whole = q / 4
        val frac = (q % 4) * 25
        return "$sign$whole,${frac.toString().padStart(2, '0')}"
    }

    /** Quarter-units → float, for chart rendering only (PRD §3.2 implementation rule). */
    fun toFloat(quarters: Int): Float = quarters / 4f

    /** Formats a fractional quarter-unit average (e.g. last-5 mean): 91.6q → "22,90". */
    fun formatAverageQuarters(avgQuarters: Double): String {
        val net = avgQuarters / 4.0
        val sign = if (net < 0) "-" else ""
        val abs = kotlin.math.abs(net)
        val scaled = kotlin.math.round(abs * 100).toLong()
        return "$sign${scaled / 100},${(scaled % 100).toString().padStart(2, '0')}"
    }

    /** Accuracy over answered questions; null when nothing was answered. */
    fun accuracyPercent(correct: Int, wrong: Int): Int? {
        val answered = correct + wrong
        if (answered == 0) return null
        return ((correct * 100.0) / answered).toInt()
    }
}
