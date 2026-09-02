package com.yks2027.tracker.core.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * PRD §11 chart contract. X is exam index (PRD §4.4 — denemes cluster; a time axis renders
 * voids); the bottom axis maps indices back to date labels. Y-values arrive in quarter-units
 * and are converted to floats only at the render boundary (PRD §3.2).
 *
 * v2.0: the renderer is platform-specific — Vico 3.x on Android (unchanged from v1.x) and
 * the MiniViz Canvas kit on desktop (Vico's multiplatform artifacts are a different, older
 * API line; adding a second chart library was ruled out). See ARCHITECTURE §Platform boundary.
 */

data class ChartPoint(val label: String, val quarters: Int)

@Composable
fun EmptyChartNote(modifier: Modifier = Modifier, text: String = "Grafik için en az 2 deneme gerekli") {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Semantic chart colors: Yanlış = theme error, Boş = neutral outline. Legend uses the same pair. */
object CountsChartColors {
    val yanlis: Color @Composable get() = MaterialTheme.colorScheme.error
    val bos: Color @Composable get() = MaterialTheme.colorScheme.outline
}

/** Single-line net trend. Auto-ranges, so negative nets extend the axis below zero. */
@Composable
expect fun NetTrendChart(points: List<ChartPoint>, modifier: Modifier = Modifier)

/** Accuracy-% line (0–100). */
@Composable
expect fun PercentTrendChart(percents: List<Int>, labels: List<String>, modifier: Modifier = Modifier)

/**
 * Boş-vs-yanlış grouped columns (PRD §4.4 — 10 blanks and 10 wrongs demand opposite
 * study strategies). Column colors come from CountsChartColors so the legend can never drift.
 */
@Composable
expect fun CountsColumnChart(yanlis: List<Int>, bos: List<Int>, labels: List<String>, modifier: Modifier = Modifier)

/**
 * v1.2 — Geçmiş Haftalar trend: study minutes as columns (start axis) + tasks done as
 * a line (end axis). Two units, two axes — no fake normalization.
 */
@Composable
expect fun WeeklyTrendChart(minutes: List<Int>, tasksDone: List<Int>, labels: List<String>, modifier: Modifier = Modifier)

@Composable
fun ChartLegend(entries: List<Pair<String, Color>>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        entries.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(color, CircleShape),
                )
                Text(
                    label,
                    Modifier.padding(start = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
