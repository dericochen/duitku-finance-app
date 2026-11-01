package com.duitku.app.presentation.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duitku.app.core.util.DateFormats
import com.duitku.app.core.util.MoneyFormatter
import com.duitku.app.domain.model.TransactionType
import com.duitku.app.presentation.components.BarChart
import com.duitku.app.presentation.components.BarGroup
import com.duitku.app.presentation.components.BudgetProgressBar
import com.duitku.app.presentation.components.EmptyState
import com.duitku.app.presentation.components.LoadingBox
import com.duitku.app.presentation.components.NeoCard
import com.duitku.app.presentation.components.SectionHeader
import com.duitku.app.presentation.components.TransactionRow
import com.duitku.app.presentation.components.budgetColor
import com.duitku.app.presentation.components.statusFor
import com.duitku.app.presentation.theme.Dimens
import com.duitku.app.presentation.theme.DuitKuTheme

@Composable
fun HomeScreen(
    onAddTransaction: (type: TransactionType) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onSeeAll: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenBackup: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(state, onAddTransaction, onOpenTransaction, onSeeAll, onOpenBudget, onOpenBackup, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    onAddTransaction: (TransactionType) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onSeeAll: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenBackup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.ownerName.isBlank()) "DuitKu" else "Halo, ${state.ownerName}",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        if (state.isLoading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.space),
        ) {
            item(key = "balance") { BalanceCard(state) }
            item(key = "quick") { QuickActions(onAddTransaction) }
            item(key = "budget") { BudgetCard(state, onOpenBudget) }
            item(key = "chart") { SpendingChartCard(state) }
            item(key = "recent-title") {
                SectionHeader(
                    title = "Transaksi terbaru",
                    actionLabel = "Lihat semua",
                    onAction = onSeeAll,
                )
            }
            if (state.recent.isEmpty()) {
                item(key = "recent-empty") {
                    NeoCard {
                        EmptyState(
                            title = "Belum ada transaksi",
                            message = "Mulai catat pemasukan atau pengeluaran pertama Anda dengan tombol di atas.",
                            modifier = Modifier.height(180.dp),
                        )
                    }
                }
            } else {
                item(key = "recent-list") {
                    NeoCard(contentPadding = 4.dp) {
                        state.recent.forEachIndexed { index, item ->
                            TransactionRow(
                                item,
                                state.currencyCode,
                                onClick = { onOpenTransaction(item.transaction.id) },
                            )
                            if (index != state.recent.lastIndex) {
                                androidx.compose.material3.HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(start = 72.dp, end = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
            item(key = "storage") { StorageCard(state, onOpenBackup) }
        }
    }
}

/** Kartu saldo premium: panel navy gelap, saldo besar, ringkasan pemasukan/pengeluaran. */
@Composable
private fun BalanceCard(state: HomeUiState) {
    val navy = DuitKuTheme.extended.navy
    val onNavy = DuitKuTheme.extended.onNavy
    val onNavyMuted = DuitKuTheme.extended.onNavyMuted
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Dimens.balanceCardShape)
            .background(navy)
            .padding(horizontal = 22.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Total saldo",
                style = MaterialTheme.typography.labelLarge,
                color = onNavyMuted,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(DuitKuTheme.extended.accent.copy(alpha = 0.18f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    state.periodLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = DuitKuTheme.extended.accent,
                )
            }
        }
        Text(
            MoneyFormatter.format(state.totalBalance, state.currencyCode),
            style = MaterialTheme.typography.displaySmall,
            color = onNavy,
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowAmount(
                "Pemasukan", state.income, state.currencyCode,
                Icons.Rounded.ArrowDownward, DuitKuTheme.extended.income, onNavyMuted, Modifier.weight(1f),
            )
            FlowAmount(
                "Pengeluaran", state.expense, state.currencyCode,
                Icons.Rounded.ArrowUpward, DuitKuTheme.extended.expense, onNavyMuted, Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FlowAmount(
    label: String,
    amount: Long,
    currency: String,
    icon: ImageVector,
    valueColor: Color,
    labelColor: Color,
    modifier: Modifier,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(valueColor.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = valueColor, modifier = Modifier.size(14.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = labelColor)
        }
        Text(
            MoneyFormatter.format(amount, currency),
            style = MaterialTheme.typography.titleMedium,
            color = DuitKuTheme.extended.onNavy,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun QuickActions(onAdd: (TransactionType) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        QuickButton("Pemasukan", Icons.Rounded.ArrowDownward, DuitKuTheme.extended.income, Modifier.weight(1f)) {
            onAdd(TransactionType.INCOME)
        }
        QuickButton("Pengeluaran", Icons.Rounded.ArrowUpward, DuitKuTheme.extended.expense, Modifier.weight(1f)) {
            onAdd(TransactionType.EXPENSE)
        }
        QuickButton("Transfer", Icons.Rounded.SwapHoriz, DuitKuTheme.extended.transfer, Modifier.weight(1f)) {
            onAdd(TransactionType.TRANSFER)
        }
    }
}

@Composable
private fun QuickButton(label: String, icon: ImageVector, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(Dimens.cardShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, Dimens.cardShape)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(Dimens.iconBoxShape)
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun BudgetCard(state: HomeUiState, onOpenBudget: () -> Unit) {
    val budget = state.budget
    NeoCard(onClick = onOpenBudget) {
        Text("Anggaran bulan ini", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        if (budget == null) {
            Text(
                "Anggaran belum diatur. Ketuk untuk membuat anggaran bulanan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val color = budgetColor(statusFor(budget.percent))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (budget.remaining >= 0) "Sisa" else "Melebihi",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        MoneyFormatter.format(kotlin.math.abs(budget.remaining), state.currencyCode),
                        style = MaterialTheme.typography.headlineSmall,
                        color = color,
                    )
                }
                Text(
                    "dari ${MoneyFormatter.format(budget.limit, state.currencyCode)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            BudgetProgressBar(budget.percent)
        }
    }
}

@Composable
private fun SpendingChartCard(state: HomeUiState) {
    NeoCard {
        Text("Pengeluaran 7 hari terakhir", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        if (state.last7Days.all { it.amount == 0L }) {
            Text(
                "Belum ada pengeluaran dalam 7 hari terakhir.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            BarChart(
                groups = state.last7Days.map { BarGroup(it.label, listOf(it.amount)) },
                seriesColors = listOf(DuitKuTheme.extended.expense),
            )
        }
    }
}

@Composable
private fun StorageCard(state: HomeUiState, onOpenBackup: () -> Unit) {
    NeoCard(onClick = onOpenBackup) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val ok = state.lastBackupAt > 0
            Box(
                Modifier
                    .size(40.dp)
                    .clip(Dimens.iconBoxShape)
                    .background(
                        (if (ok) DuitKuTheme.extended.income else MaterialTheme.colorScheme.onSurfaceVariant)
                            .copy(alpha = 0.14f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (ok) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = if (ok) DuitKuTheme.extended.income else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "${state.monthsStored} bulan data tersimpan · ${state.closedCount} arsip tertutup",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    if (ok) "Backup terakhir: ${DateFormats.dateTime(state.lastBackupAt)}"
                    else if (state.backupConfigured) "Backup terakhir: belum ada"
                    else "Backup terakhir: belum diatur",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!state.integrityOk) {
                    Text(
                        "Pemeriksaan database menemukan masalah",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun HomePreview() {
    DuitKuTheme(dynamicColor = false) {
        HomeContent(
            state = HomeUiState(
                isLoading = false, ownerName = "Dewi", totalBalance = 12_450_000, periodLabel = "November 2026",
                income = 8_500_000, expense = 3_200_000, budget = BudgetSummary(5_000_000, 4_100_000),
                last7Days = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min").mapIndexed { i, d -> DailyExpense(d, (i + 1) * 15_000L) },
                monthsStored = 12, closedCount = 11, lastBackupAt = System.currentTimeMillis(),
            ),
            onAddTransaction = {}, onOpenTransaction = {}, onSeeAll = {}, onOpenBudget = {}, onOpenBackup = {},
        )
    }
}
