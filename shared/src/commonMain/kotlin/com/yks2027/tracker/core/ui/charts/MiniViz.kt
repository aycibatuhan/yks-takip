package com.yks2027.tracker.core.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yks2027.tracker.core.model.HeatStripBuckets

/**
 * v1.2 "görsel paso" — small reusable Canvas components where Vico is overkill.
 * Design rules (agreed scope): theme tokens only, restrained color (category/error
 * accents only where the color IS the information), numbers always shown alongside
 * graphics by the call sites, honest empty states for <2 points.
 */

/** Tiny inline trend line (KPI cards, Geçmiş Haftalar rows). No axes, no labels. */
@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    showLastDot: Boolean = true,
) {
    if (values.size < 2) {
        Box(modifier) // not enough points — reserve the space, draw nothing
        return
    }
    val min = values.min()
    val max = values.max()
    val span = (max - min).takeIf { it > 0f } ?: 1f
    Canvas(modifier) {
        val stepX = size.width / (values.size - 1)
        val pad = 2.dp.toPx()
        val usable = size.height - pad * 2
        fun pointAt(i: Int) = Offset(
            x = stepX * i,
            y = pad + usable * (1f - (values[i] - min) / span),
        )
        val path = Path().apply {
            moveTo(pointAt(0).x, pointAt(0).y)
            for (i in 1 until values.size) lineTo(pointAt(i).x, pointAt(i).y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        if (showLastDot) drawCircle(color, radius = 3.dp.toPx(), center = pointAt(values.lastIndex))
    }
}

/** Circular progress dial; content (the number) renders in the center. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    stroke: Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable () -> Unit = {},
) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(strokePx, cap = StrokeCap.Round),
            )
            if (clamped > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * clamped,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(strokePx, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

data class BarSegment(val value: Float, val color: Color)

/**
 * Horizontal segmented bar. [fillFraction] scales the whole bar against the row's max
 * (ranking lists); segments split that length proportionally (e.g. hata türleri).
 */
@Composable
fun SegmentedBar(
    segments: List<BarSegment>,
    modifier: Modifier = Modifier,
    fillFraction: Float = 1f,
    height: Dp = 8.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val total = segments.sumOf { it.value.toDouble() }.toFloat()
    Canvas(modifier.height(height)) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(trackColor, size = size, cornerRadius = radius)
        if (total <= 0f) return@Canvas
        val barWidth = size.width * fillFraction.coerceIn(0f, 1f)
        var x = 0f
        segments.filter { it.value > 0f }.forEach { segment ->
            val w = barWidth * (segment.value / total)
            drawRoundRect(
                segment.color,
                topLeft = Offset(x, 0f),
                size = Size(w, size.height),
                cornerRadius = radius,
            )
            x += w
        }
    }
}

/** GitHub-style activity strip: columns = weeks (old→new), rows = Mon..Sun. */
@Composable
fun HeatStrip(
    grid: List<List<Int?>>,
    modifier: Modifier = Modifier,
    cell: Dp = 9.dp,
    gap: Dp = 2.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    emptyColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Canvas(
        modifier.size(
            width = cell * grid.size + gap * (grid.size - 1).coerceAtLeast(0),
            height = cell * 7 + gap * 6,
        ),
    ) {
        val cellPx = cell.toPx()
        val gapPx = gap.toPx()
        val radius = CornerRadius(2.dp.toPx())
        grid.forEachIndexed { col, weekDays ->
            weekDays.forEachIndexed { rowIndex, count ->
                if (count == null) return@forEachIndexed // future day — leave blank
                val level = HeatStripBuckets.level(count)
                val cellColor = if (level == 0) emptyColor else color.copy(alpha = 0.25f + 0.25f * (level - 1))
                drawRoundRect(
                    cellColor,
                    topLeft = Offset(col * (cellPx + gapPx), rowIndex * (cellPx + gapPx)),
                    size = Size(cellPx, cellPx),
                    cornerRadius = radius,
                )
            }
        }
    }
}

/** Shared legend row for segmented bars (label + colored dot + value). */
@Composable
fun SegmentLegend(entries: List<Triple<String, Color, String>>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        entries.forEach { (label, color, value) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(7.dp)) { drawCircle(color) }
                Text(
                    " $label $value",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Semantic colors for the hata-türü segments (Zayıf Konular bars). Color carries the
 * information here, so each type gets a stable, theme-aware accent; wrongs without a
 * diagnosis render neutral.
 */
object ErrorTypeColors {
    val bilgi: Color @Composable get() = MaterialTheme.colorScheme.primary
    val islem: Color @Composable get() = MaterialTheme.colorScheme.tertiary
    val dikkat: Color @Composable get() = Color(0xFFE8A13D) // amber accent, works on both themes
    val sure: Color @Composable get() = MaterialTheme.colorScheme.error
    val belirsiz: Color @Composable get() = MaterialTheme.colorScheme.outline

    @Composable
    fun forName(name: String?): Color = when (name) {
        "BILGI" -> bilgi
        "ISLEM" -> islem
        "DIKKAT" -> dikkat
        "SURE" -> sure
        else -> belirsiz
    }
}

/** Weekly-trend chart row helper: fixed-height sparkline block with an inline caption. */
@Composable
fun SparklineWithCaption(
    values: List<Float>,
    caption: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Column(modifier) {
        Sparkline(values, Modifier.fillMaxWidth().height(28.dp), color = color)
        Text(
            caption,
            Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
