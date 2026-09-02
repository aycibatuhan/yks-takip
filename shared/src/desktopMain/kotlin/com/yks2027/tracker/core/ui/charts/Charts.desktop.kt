package com.yks2027.tracker.core.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Desktop renderer — pure Compose Canvas (the MiniViz kit grown up): left value axis with
 * "nice" ticks, optional right axis for a second unit, index-based bottom labels, lines
 * with point markers and grouped columns. Numbers next to the chart are still always
 * rendered by the callers (PRD §10.3), so this stays a faithful, lightweight view.
 */

private class Axis(val min: Float, val max: Float, val ticks: List<Float>)

private fun niceAxis(values: List<Float>, includeZero: Boolean, targetTicks: Int = 5): Axis {
    var lo = values.minOrNull() ?: 0f
    var hi = values.maxOrNull() ?: 1f
    if (includeZero) { lo = minOf(lo, 0f); hi = maxOf(hi, 0f) }
    if (hi - lo < 1e-3f) { hi = lo + 1f }
    val raw = (hi - lo) / (targetTicks - 1)
    val mag = 10f.pow(floor(log10(raw.toDouble())).toFloat())
    val norm = raw / mag
    val step = (when { norm <= 1f -> 1f; norm <= 2f -> 2f; norm <= 2.5f -> 2.5f; norm <= 5f -> 5f; else -> 10f }) * mag
    val start = floor(lo / step) * step
    val end = ceil(hi / step) * step
    val ticks = generateSequence(start) { it + step }.takeWhile { it <= end + step / 2 }.toList()
    return Axis(start, end, ticks)
}

private fun fmt(v: Float): String =
    if (abs(v - v.toInt()) < 1e-3f) v.toInt().toString() else ((v * 100).toInt() / 100f).toString().replace('.', ',')

private class Frame(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val w get() = right - left
    val h get() = bottom - top
}

private fun DrawScope.drawFrame(
    measurer: TextMeasurer,
    style: TextStyle,
    gridColor: Color,
    left: Axis,
    right: Axis?,
    labels: List<String>,
): Frame {
    val leftW = left.ticks.maxOf { measurer.measure(fmt(it), style).size.width } + 8.dp.toPx()
    val rightW = right?.let { r -> r.ticks.maxOf { measurer.measure(fmt(it), style).size.width } + 8.dp.toPx() } ?: 4.dp.toPx()
    val labelH = measurer.measure("0", style).size.height
    val f = Frame(leftW, 6.dp.toPx(), size.width - rightW, size.height - labelH - 8.dp.toPx())
    left.ticks.forEach { t ->
        val y = f.bottom - (t - left.min) / (left.max - left.min) * f.h
        drawLine(gridColor, Offset(f.left, y), Offset(f.right, y), strokeWidth = 1f)
        val m = measurer.measure(fmt(t), style)
        drawText(m, topLeft = Offset(f.left - m.size.width - 4.dp.toPx(), y - m.size.height / 2))
    }
    right?.ticks?.forEach { t ->
        val y = f.bottom - (t - right.min) / (right.max - right.min) * f.h
        val m = measurer.measure(fmt(t), style)
        drawText(m, topLeft = Offset(f.right + 4.dp.toPx(), y - m.size.height / 2))
    }
    // Bottom labels: at most ~8, evenly thinned; first and last always shown.
    val n = labels.size
    if (n > 0) {
        val every = maxOf(1, ceil(n / 8f).toInt())
        labels.forEachIndexed { i, label ->
            if (i % every != 0 && i != n - 1) return@forEachIndexed
            val x = xAt(f, i, n)
            val m = measurer.measure(label, style)
            drawText(m, topLeft = Offset((x - m.size.width / 2).coerceIn(0f, size.width - m.size.width), f.bottom + 4.dp.toPx()))
        }
    }
    return f
}

private fun xAt(f: Frame, i: Int, n: Int): Float =
    if (n <= 1) f.left + f.w / 2 else f.left + f.w * (i + 0.5f) / n

private fun DrawScope.drawLineSeries(f: Frame, axis: Axis, values: List<Float>, color: Color) {
    val n = values.size
    val path = Path()
    values.forEachIndexed { i, v ->
        val x = xAt(f, i, n)
        val y = f.bottom - (v - axis.min) / (axis.max - axis.min) * f.h
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color, style = Stroke(width = 2.5f.dp.toPx(), cap = StrokeCap.Round))
    values.forEachIndexed { i, v ->
        val x = xAt(f, i, n)
        val y = f.bottom - (v - axis.min) / (axis.max - axis.min) * f.h
        drawCircle(color, radius = 3.5f.dp.toPx(), center = Offset(x, y))
    }
}

private fun DrawScope.drawColumns(f: Frame, axis: Axis, series: List<Pair<List<Float>, Color>>) {
    val n = series.firstOrNull()?.first?.size ?: return
    val slot = f.w / n
    val group = slot * 0.6f
    val barW = group / series.size
    val zeroY = f.bottom - (0f - axis.min) / (axis.max - axis.min) * f.h
    series.forEachIndexed { s, (values, color) ->
        values.forEachIndexed { i, v ->
            val cx = xAt(f, i, n)
            val x = cx - group / 2 + s * barW
            val y = f.bottom - (v - axis.min) / (axis.max - axis.min) * f.h
            drawRect(color, topLeft = Offset(x, minOf(y, zeroY)), size = Size(barW * 0.9f, abs(zeroY - y)))
        }
    }
}

@Composable
private fun chartStyle(): Pair<TextStyle, Color> =
    TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) to
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

@Composable
actual fun NetTrendChart(points: List<ChartPoint>, modifier: Modifier) {
    if (points.size < 2) {
        EmptyChartNote(modifier)
        return
    }
    val measurer = rememberTextMeasurer()
    val (style, grid) = chartStyle()
    val color = MaterialTheme.colorScheme.primary
    val values = points.map { it.quarters / 4f }
    val axis = niceAxis(values, includeZero = values.any { it < 0f })
    Canvas(modifier) {
        val f = drawFrame(measurer, style, grid, axis, null, points.map { it.label })
        drawLineSeries(f, axis, values, color)
    }
}

@Composable
actual fun PercentTrendChart(percents: List<Int>, labels: List<String>, modifier: Modifier) {
    if (percents.size < 2) {
        EmptyChartNote(modifier)
        return
    }
    val measurer = rememberTextMeasurer()
    val (style, grid) = chartStyle()
    val color = MaterialTheme.colorScheme.tertiary
    val values = percents.map { it.toFloat() }
    val axis = Axis(0f, 100f, listOf(0f, 25f, 50f, 75f, 100f))
    Canvas(modifier) {
        val f = drawFrame(measurer, style, grid, axis, null, labels)
        drawLineSeries(f, axis, values, color)
    }
}

@Composable
actual fun CountsColumnChart(yanlis: List<Int>, bos: List<Int>, labels: List<String>, modifier: Modifier) {
    if (yanlis.size < 2) {
        EmptyChartNote(modifier)
        return
    }
    val measurer = rememberTextMeasurer()
    val (style, grid) = chartStyle()
    val cYanlis = CountsChartColors.yanlis
    val cBos = CountsChartColors.bos
    val a = yanlis.map { it.toFloat() }
    val b = bos.map { it.toFloat() }
    val axis = niceAxis(a + b, includeZero = true)
    Canvas(modifier) {
        val f = drawFrame(measurer, style, grid, axis, null, labels)
        drawColumns(f, axis, listOf(a to cYanlis, b to cBos))
    }
}

@Composable
actual fun WeeklyTrendChart(minutes: List<Int>, tasksDone: List<Int>, labels: List<String>, modifier: Modifier) {
    if (labels.size < 2) {
        EmptyChartNote(modifier, "Grafik için en az 2 geçmiş hafta gerekli")
        return
    }
    val measurer = rememberTextMeasurer()
    val (style, grid) = chartStyle()
    val colColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val lineColor = MaterialTheme.colorScheme.secondary
    val m = minutes.map { it.toFloat() }
    val t = tasksDone.map { it.toFloat() }
    val left = niceAxis(m, includeZero = true)
    val right = niceAxis(t, includeZero = true)
    Canvas(modifier) {
        val f = drawFrame(measurer, style, grid, left, right, labels)
        drawColumns(f, left, listOf(m to colColor))
        drawLineSeries(f, right, t, lineColor)
    }
}
