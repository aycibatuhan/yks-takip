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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.Fill

/**
 * PRD §11 — Vico 3.x chart components (replaces the M1 Canvas TrendChart).
 * X is exam index (PRD §4.4 — denemes cluster; a time axis renders voids); the bottom
 * axis maps indices back to date labels. Y-values arrive in quarter-units and are
 * converted to floats only here, at the render boundary (PRD §3.2).
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

/** Single-line net trend. Auto-ranges, so negative nets extend the axis below zero. */
@Composable
fun NetTrendChart(points: List<ChartPoint>, modifier: Modifier = Modifier) {
    if (points.size < 2) {
        EmptyChartNote(modifier)
        return
    }
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points) {
        modelProducer.runTransaction {
            lineModel { series(y = points.map { it.quarters / 4f }) }
        }
    }
    val labels = points.map { it.label }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = indexLabelFormatter(labels),
            ),
        ),
        modelProducer = modelProducer,
        modifier = modifier,
    )
}

/** Accuracy-% line (0–100). */
@Composable
fun PercentTrendChart(percents: List<Int>, labels: List<String>, modifier: Modifier = Modifier) {
    if (percents.size < 2) {
        EmptyChartNote(modifier)
        return
    }
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(percents) {
        modelProducer.runTransaction {
            lineModel { series(y = percents) }
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = indexLabelFormatter(labels),
            ),
        ),
        modelProducer = modelProducer,
        modifier = modifier,
    )
}

/** Semantic chart colors: Yanlış = theme error, Boş = neutral outline. Legend uses the same pair. */
object CountsChartColors {
    val yanlis: Color @Composable get() = MaterialTheme.colorScheme.error
    val bos: Color @Composable get() = MaterialTheme.colorScheme.outline
}

/**
 * Boş-vs-yanlış grouped columns (PRD §4.4 — 10 blanks and 10 wrongs demand opposite
 * study strategies). Column colors are set explicitly from CountsChartColors so the
 * legend can never drift from the bars (caught on the device tour).
 */
@Composable
fun CountsColumnChart(
    yanlis: List<Int>,
    bos: List<Int>,
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    if (yanlis.size < 2) {
        EmptyChartNote(modifier)
        return
    }
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(yanlis, bos) {
        modelProducer.runTransaction {
            columnModel {
                series(y = yanlis)
                series(y = bos)
            }
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                ColumnCartesianLayer.ColumnProvider.series(
                    rememberLineComponent(fill = Fill(CountsChartColors.yanlis), thickness = 10.dp),
                    rememberLineComponent(fill = Fill(CountsChartColors.bos), thickness = 10.dp),
                ),
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = indexLabelFormatter(labels),
            ),
        ),
        modelProducer = modelProducer,
        modifier = modifier,
    )
}

/**
 * v1.2 — Geçmiş Haftalar trend: study minutes as columns (start axis) + tasks done as
 * a line (end axis). Two units, two axes — no fake normalization.
 */
@Composable
fun WeeklyTrendChart(
    minutes: List<Int>,
    tasksDone: List<Int>,
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    if (labels.size < 2) {
        EmptyChartNote(modifier, "Grafik için en az 2 geçmiş hafta gerekli")
        return
    }
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(minutes, tasksDone) {
        modelProducer.runTransaction {
            columnModel { series(y = minutes) }
            lineModel { series(y = tasksDone) }
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                ColumnCartesianLayer.ColumnProvider.series(
                    rememberLineComponent(
                        fill = Fill(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                        thickness = 14.dp,
                    ),
                ),
                verticalAxisPosition = Axis.Position.Vertical.Start,
            ),
            rememberLineCartesianLayer(verticalAxisPosition = Axis.Position.Vertical.End),
            startAxis = VerticalAxis.rememberStart(),
            endAxis = VerticalAxis.rememberEnd(),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = indexLabelFormatter(labels),
            ),
        ),
        modelProducer = modelProducer,
        modifier = modifier,
    )
}

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

private fun indexLabelFormatter(labels: List<String>): CartesianValueFormatter =
    CartesianValueFormatter { _, value, _ ->
        labels.getOrElse(value.toInt()) { "" }
    }
