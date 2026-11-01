package com.duitku.app.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duitku.app.core.util.MoneyFormatter
import com.duitku.app.presentation.theme.DuitKuTheme

/** Satu kelompok batang (mis. satu hari / satu bulan) dengan beberapa seri nilai. */
data class BarGroup(val label: String, val values: List<Long>)

data class ChartSlice(val label: String, val value: Long, val color: Color)

data class LinePoint(val label: String, val value: Long)

@Composable
private fun rememberChartProgress(key: Any?): Float {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { progress.animateTo(1f, tween(600)) }
    return progress.value
}

/** Diagram batang berkelompok (1 seri = pengeluaran harian; 2 seri = pemasukan vs pengeluaran). */
@Composable
fun BarChart(
    groups: List<BarGroup>,
    seriesColors: List<Color>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
) {
    val progress = rememberChartProgress(groups)
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (groups.isEmpty()) return@Canvas
        val bottomPad = 20.dp.toPx()
        val topPad = 16.dp.toPx()
        val chartH = size.height - bottomPad - topPad
        val maxV = groups.maxOf { g -> g.values.maxOrNull() ?: 0L }.coerceAtLeast(1L).toFloat()

        drawLine(gridColor, Offset(0f, topPad + chartH), Offset(size.width, topPad + chartH), 1.dp.toPx())
        val maxLabel = measurer.measure(MoneyFormatter.compact(maxV.toLong()), labelStyle)
        drawText(maxLabel, topLeft = Offset(0f, 0f))

        val groupW = size.width / groups.size
        val series = groups.maxOf { it.values.size }.coerceAtLeast(1)
        val barW = (groupW * 0.7f / series).coerceAtMost(28.dp.toPx())
        groups.forEachIndexed { gi, group ->
            val groupStart = gi * groupW + (groupW - barW * series) / 2f
            group.values.forEachIndexed { si, value ->
                val h = chartH * (value / maxV) * progress
                drawRoundRect(
                    color = seriesColors.getOrElse(si) { Color.Gray },
                    topLeft = Offset(groupStart + si * barW, topPad + chartH - h),
                    size = Size(barW * 0.9f, h.coerceAtLeast(if (value > 0) 2f else 0f)),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
            val label = measurer.measure(group.label, labelStyle)
            drawText(label, topLeft = Offset(gi * groupW + (groupW - label.size.width) / 2f, topPad + chartH + 4.dp.toPx()))
        }
    }
}

/** Diagram donat dengan teks di tengah. */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    centerTop: String = "",
    centerBottom: String = "",
) {
    val progress = rememberChartProgress(slices)
    val total = slices.sumOf { it.value }.coerceAtLeast(1L).toFloat()
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = size.toPx() * 0.16f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            if (slices.isEmpty() || slices.sumOf { it.value } == 0L) {
                drawArc(emptyColor, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                return@Canvas
            }
            var start = -90f
            slices.forEach { slice ->
                val sweep = 360f * (slice.value / total) * progress
                drawArc(
                    color = slice.color, startAngle = start, sweepAngle = (sweep - 1.5f).coerceAtLeast(0.5f),
                    useCenter = false, topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Butt),
                )
                start += sweep
            }
        }
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (centerTop.isNotEmpty()) Text(centerTop, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (centerBottom.isNotEmpty()) Text(centerBottom, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Grafik garis (tren saldo / arus kas). Nilai negatif didukung. */
@Composable
fun LineChart(
    points: List<LinePoint>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    labelEvery: Int = 1,
) {
    val progress = rememberChartProgress(points)
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (points.size < 2) return@Canvas
        val bottomPad = 20.dp.toPx()
        val topPad = 16.dp.toPx()
        val chartH = size.height - bottomPad - topPad
        val maxV = points.maxOf { it.value }
        val minV = minOf(points.minOf { it.value }, 0L)
        val range = (maxV - minV).coerceAtLeast(1L).toFloat()
        fun y(v: Long) = topPad + chartH * (1f - (v - minV) / range)
        val stepX = size.width / (points.size - 1)

        drawLine(gridColor, Offset(0f, y(0)), Offset(size.width, y(0)), 1.dp.toPx())
        drawText(measurer.measure(MoneyFormatter.compact(maxV), labelStyle), topLeft = Offset(0f, 0f))

        val visible = ((points.size - 1) * progress).toInt()
        val path = Path()
        for (i in 0..visible) {
            val p = Offset(i * stepX, y(points[i].value))
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, lineColor, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        for (i in 0..visible) drawCircle(lineColor, 3.dp.toPx(), Offset(i * stepX, y(points[i].value)))

        points.forEachIndexed { i, p ->
            if (labelEvery > 0 && i % labelEvery == 0) {
                val layout = measurer.measure(p.label, labelStyle)
                val x = (i * stepX - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                drawText(layout, topLeft = Offset(x, topPad + chartH + 4.dp.toPx()))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChartsPreview() {
    DuitKuTheme(dynamicColor = false) {
        androidx.compose.foundation.layout.Column {
            BarChart(
                groups = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min").mapIndexed { i, d -> BarGroup(d, listOf((i + 1) * 20_000L)) },
                seriesColors = listOf(Color(0xFFC62828)),
            )
            DonutChart(
                slices = listOf(ChartSlice("A", 60, Color(0xFF0B6B4F)), ChartSlice("B", 40, Color(0xFFE65100))),
                centerTop = "Total", centerBottom = "Rp 100rb",
            )
            LineChart(points = listOf(1L, 5L, 3L, 8L).mapIndexed { i, v -> LinePoint("$i", v * 100_000) }, lineColor = Color(0xFF0277BD))
        }
    }
}
