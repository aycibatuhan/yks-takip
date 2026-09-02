package com.yks2027.tracker.core.ui.charts

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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

/** Android renderer — Vico 3.x, byte-for-byte the v1.3 implementation. */

@Composable
actual fun NetTrendChart(points: List<ChartPoint>, modifier: Modifier) {
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

@Composable
actual fun PercentTrendChart(percents: List<Int>, labels: List<String>, modifier: Modifier) {
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

@Composable
actual fun CountsColumnChart(
    yanlis: List<Int>,
    bos: List<Int>,
    labels: List<String>,
    modifier: Modifier,
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

@Composable
actual fun WeeklyTrendChart(
    minutes: List<Int>,
    tasksDone: List<Int>,
    labels: List<String>,
    modifier: Modifier,
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

private fun indexLabelFormatter(labels: List<String>): CartesianValueFormatter =
    CartesianValueFormatter { _, value, _ ->
        labels.getOrElse(value.toInt()) { "" }
    }
