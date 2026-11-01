package com.duitku.app.presentation.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duitku.app.core.util.DateFormats
import com.duitku.app.core.util.MoneyFormatter
import com.duitku.app.domain.model.BalancePoint
import com.duitku.app.domain.model.CategoryStat
import com.duitku.app.domain.model.Comparison
import com.duitku.app.domain.model.MonthBar
import com.duitku.app.domain.model.StatsData
import com.duitku.app.domain.model.StatsMode
import com.duitku.app.domain.model.StatsRange
import com.duitku.app.presentation.components.BarChart
import com.duitku.app.presentation.components.BarGroup
import com.duitku.app.presentation.components.ChartSlice
import com.duitku.app.presentation.components.DatePickerDialogLocal
import com.duitku.app.presentation.components.DonutChart
import com.duitku.app.presentation.components.EmptyState
import com.duitku.app.presentation.components.ErrorState
import com.duitku.app.presentation.components.IconBadge
import com.duitku.app.presentation.components.LineChart
import com.duitku.app.presentation.components.LinePoint
import com.duitku.app.presentation.components.LoadingBox
import com.duitku.app.presentation.theme.DuitKuTheme
import java.time.LocalDate
import java.util.Locale

@Composable
fun StatsScreen(modifier: Modifier = Modifier, viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StatsContent(
        state = state, modifier = modifier,
        onMode = viewModel::setMode, onPrevious = viewModel::previous, onNext = viewModel::next,
        onCustomRange = viewModel::setCustomRange, onMonthsBack = viewModel::setMonthsBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsContent(
    state: StatsUiState,
    onMode: (StatsMode) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCustomRange: (LocalDate?, LocalDate?) -> Unit,
    onMonthsBack: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pick by remember { mutableStateOf<String?>(null) }
    Scaffold(modifier = modifier, topBar = { TopAppBar(title = { Text("Statistik") }) }) { padding ->
        Column(Modifier.padding(padding)) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(StatsMode.entries) { mode ->
                    FilterChip(selected = state.request.mode == mode, onClick = { onMode(mode) }, label = { Text(mode.label) })
                }
            }
            when {
                state.error != null -> ErrorState(state.error)
                state.isLoading || state.data == null -> LoadingBox()
                else -> {
                    val data = state.data
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item(key = "range") {
                            RangeHeader(state, data.range, onPrevious, onNext, onPickFrom = { pick = "from" }, onPickTo = { pick = "to" })
                        }
                        if (data.isEmpty) {
                            item(key = "empty") {
                                EmptyState(title = "Belum ada data", message = "Tidak ada transaksi pada periode ini.", modifier = Modifier.padding(vertical = 24.dp))
                            }
                        } else {
                            item(key = "summary") { SummaryRow(data, state.currencyCode) }
                            item(key = "compare") { ComparisonCard(data, state.currencyCode) }
                            item(key = "donut") { ExpenseDonutCard(data, state.currencyCode) }
                            item(key = "top5") { TopCategoriesCard(data, state.currencyCode) }
                        }
                        item(key = "bars") { MonthlyBarsCard(state, data.monthlyBars, onMonthsBack) }
                        if (data.balanceTrend.size >= 2) item(key = "trend") { TrendCard(data.balanceTrend) }
                    }
                }
            }
        }
    }

    pick?.let { which ->
        val request = state.request
        DatePickerDialogLocal(
            initial = (if (which == "from") request.customFrom else request.customTo) ?: LocalDate.now(),
            onConfirm = { date ->
                pick = null
                if (which == "from") onCustomRange(date, request.customTo ?: date) else onCustomRange(request.customFrom ?: date, date)
            },
            onDismiss = { pick = null },
        )
    }
}

@Composable
private fun RangeHeader(
    state: StatsUiState,
    range: StatsRange,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.canNavigate) {
                IconButton(onClick = onPrevious) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Sebelumnya") }
            }
            Text(range.label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f, fill = state.canNavigate), textAlign = TextAlign.Center)
            if (state.canNavigate) {
                IconButton(onClick = onNext, enabled = state.canGoNext) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Berikutnya") }
            }
        }
        if (state.request.mode == StatsMode.CUSTOM) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onPickFrom, label = { Text("Dari ${DateFormats.date(range.start)}") })
                AssistChip(onClick = onPickTo, label = { Text("Sampai ${DateFormats.date(range.endInclusive)}") })
            }
        }
    }
}

@Composable
private fun SummaryRow(data: StatsData, currency: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryTile("Pemasukan", MoneyFormatter.format(data.income, currency), DuitKuTheme.extended.income, Modifier.weight(1f))
        SummaryTile("Pengeluaran", MoneyFormatter.format(data.expense, currency), DuitKuTheme.extended.expense, Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryTile("Selisih", MoneyFormatter.format(data.net, currency, showPlusSign = true), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        SummaryTile(
            "Tingkat tabungan",
            data.savingsRate?.let { "${formatPercent(it)}%" } ?: "-",
            MaterialTheme.colorScheme.onSurface,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryTile(label: String, value: String, color: Color, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1)
        }
    }
}

@Composable
private fun ComparisonCard(data: StatsData, currency: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Dibanding ${data.comparisonLabel}", style = MaterialTheme.typography.titleMedium)
            ComparisonLine("Pengeluaran", data.expenseComparison, currency, higherIsBad = true)
            ComparisonLine("Pemasukan", data.incomeComparison, currency, higherIsBad = false)
        }
    }
}

@Composable
private fun ComparisonLine(label: String, c: Comparison, currency: String, higherIsBad: Boolean) {
    val pct = c.percent
    val up = c.delta > 0
    val bad = if (higherIsBad) up else !up && c.delta != 0L
    val color = when {
        c.delta == 0L -> MaterialTheme.colorScheme.onSurfaceVariant
        bad -> DuitKuTheme.extended.expense
        else -> DuitKuTheme.extended.income
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            if (up) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
            contentDescription = null, tint = color,
        )
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${MoneyFormatter.format(c.current, currency)} (sebelumnya ${MoneyFormatter.format(c.previous, currency)})",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            when {
                pct == null && c.current > 0 -> "baru"
                pct == null -> "-"
                c.delta == 0L -> "sama"
                else -> (if (up) "naik " else "turun ") + formatPercent(kotlin.math.abs(pct)) + "%"
            },
            style = MaterialTheme.typography.titleMedium, color = color,
        )
    }
}

@Composable
private fun ExpenseDonutCard(data: StatsData, currency: String) {
    if (data.expenseByCategory.isEmpty()) return
    val slices = donutSlices(data.expenseByCategory)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Pengeluaran per kategori", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
            DonutChart(slices = slices, centerTop = "Total", centerBottom = MoneyFormatter.compact(data.expense))
            slices.forEach { s ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(s.color))
                    Text(s.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(MoneyFormatter.format(s.value, currency), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun donutSlices(stats: List<CategoryStat>): List<ChartSlice> {
    val top = stats.take(6).map { ChartSlice(it.name, it.total, Color(it.colorArgb)) }
    val rest = stats.drop(6).sumOf { it.total }
    return if (rest > 0) top + ChartSlice("Lainnya", rest, Color(0xFF9E9E9E)) else top
}

@Composable
private fun TopCategoriesCard(data: StatsData, currency: String) {
    if (data.topExpense.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Top 5 pengeluaran terbesar", style = MaterialTheme.typography.titleMedium)
            data.topExpense.forEachIndexed { index, stat ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${index + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
                    IconBadge(stat.iconKey, stat.colorArgb, size = 36.dp)
                    Column(Modifier.weight(1f)) {
                        Text(stat.name, style = MaterialTheme.typography.bodyLarge)
                        Text("${stat.count} transaksi · ${formatPercent(stat.percent)}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(MoneyFormatter.format(stat.total, currency), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun MonthlyBarsCard(state: StatsUiState, bars: List<MonthBar>, onMonthsBack: (Int) -> Unit) {
    if (bars.isEmpty()) return
    val fixed12 = state.request.mode == StatsMode.ROLLING_12
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pemasukan vs pengeluaran per bulan", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            if (!fixed12) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(6, 12).forEach { n ->
                        FilterChip(selected = state.request.monthsBack == n, onClick = { onMonthsBack(n) }, label = { Text("$n bulan") })
                    }
                }
            }
            BarChart(
                groups = bars.map { BarGroup(it.label, listOf(it.income, it.expense)) },
                seriesColors = listOf(DuitKuTheme.extended.income, DuitKuTheme.extended.expense),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("■ Pemasukan", color = DuitKuTheme.extended.income, style = MaterialTheme.typography.labelMedium)
                Text("■ Pengeluaran", color = DuitKuTheme.extended.expense, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun TrendCard(points: List<BalancePoint>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tren saldo", style = MaterialTheme.typography.titleMedium)
            LineChart(
                points = points.map { LinePoint(it.label, it.balance) },
                lineColor = MaterialTheme.colorScheme.primary,
                labelEvery = (points.size / 6).coerceAtLeast(1),
            )
        }
    }
}

private fun formatPercent(value: Double): String =
    String.format(Locale.forLanguageTag("id-ID"), "%.1f", value).removeSuffix(",0")

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun StatsPreview() {
    val cats = listOf(
        CategoryStat(1, "Makanan", 0xFFE65100.toInt(), "food", 1_200_000, 30, 40.0),
        CategoryStat(2, "Tagihan", 0xFFC62828.toInt(), "bill", 1_800_000, 5, 60.0),
    )
    DuitKuTheme(dynamicColor = false) {
        StatsContent(
            state = StatsUiState(
                isLoading = false,
                data = StatsData(
                    range = StatsRange(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), "November 2026"),
                    income = 8_500_000, expense = 3_000_000, transactionCount = 35,
                    expenseByCategory = cats, incomeByCategory = emptyList(),
                    monthlyBars = listOf("Sep", "Okt", "Nov").map { MonthBar(it, 8_000_000, 3_000_000) },
                    balanceTrend = (1..10).map { BalancePoint("$it", it * 500_000L) },
                    expenseComparison = Comparison(3_000_000, 2_500_000), incomeComparison = Comparison(8_500_000, 8_500_000),
                    comparisonLabel = "bulan lalu",
                ),
            ),
            onMode = {}, onPrevious = {}, onNext = {}, onCustomRange = { _, _ -> }, onMonthsBack = {},
        )
    }
}
