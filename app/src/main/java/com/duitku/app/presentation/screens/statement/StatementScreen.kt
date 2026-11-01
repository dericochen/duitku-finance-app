package com.duitku.app.presentation.screens.statement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duitku.app.core.util.DateFormats
import com.duitku.app.core.util.MoneyFormatter
import com.duitku.app.core.util.toLocalDate
import com.duitku.app.domain.model.MonthRow
import com.duitku.app.domain.model.TransactionType
import com.duitku.app.domain.usecase.statement.Checksum
import com.duitku.app.presentation.components.ConfirmDialog
import com.duitku.app.presentation.components.DayHeader
import com.duitku.app.presentation.components.EmptyState
import com.duitku.app.presentation.components.LoadingBox
import com.duitku.app.presentation.components.TransactionRow
import com.duitku.app.presentation.theme.DuitKuTheme

@Composable
fun StatementScreen(
    onOpenTransaction: (Long) -> Unit,
    onSearch: () -> Unit,
    onExport: (periodYm: Int) -> Unit,
    onAudit: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: StatementViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var verifyMessage by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StatementEvent.Message -> snackbar.showSnackbar(event.text)
                is StatementEvent.VerifyDone -> verifyMessage = event.valid to event.message
            }
        }
    }

    StatementContent(
        state = state,
        snackbar = snackbar,
        modifier = modifier,
        onBack = onBack,
        onPrevious = viewModel::previous,
        onNext = viewModel::next,
        onSelectWallet = viewModel::selectWallet,
        onOpenTransaction = onOpenTransaction,
        onSearch = onSearch,
        onExport = { onExport(state.periodYm) },
        onAudit = onAudit,
        onClose = viewModel::closeMonth,
        onReopen = viewModel::reopenMonth,
        onVerify = viewModel::verify,
    )

    verifyMessage?.let { (valid, message) ->
        ConfirmDialog(
            title = if (valid) "Verifikasi berhasil" else "Verifikasi gagal",
            message = message,
            confirmLabel = "OK",
            dismissLabel = "Tutup",
            onConfirm = { verifyMessage = null },
            onDismiss = { verifyMessage = null },
            destructive = !valid,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementContent(
    state: StatementUiState,
    snackbar: SnackbarHostState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectWallet: (Long?) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onSearch: () -> Unit,
    onExport: () -> Unit,
    onAudit: () -> Unit,
    onClose: () -> Unit,
    onReopen: (String) -> Unit,
    onVerify: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    var confirmClose by remember { mutableStateOf(false) }
    var confirmReopen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Mutasi") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali") }
                    }
                },
                actions = {
                    IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, contentDescription = "Cari transaksi") }
                    IconButton(onClick = onAudit) { Icon(Icons.Rounded.History, contentDescription = "Audit log") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val month = state.month
        if (state.isLoading || month == null) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item(key = "nav") { MonthNavigator(state, onPrevious, onNext) }
            item(key = "wallets") { WalletFilter(state, onSelectWallet) }
            item(key = "summary") { SummaryCard(state) }
            if (state.isClosed) item(key = "closed") { ClosedBanner(state, onVerify) }
            item(key = "actions") {
                ActionRow(
                    state = state,
                    onExport = onExport,
                    onClose = { confirmClose = true },
                    onReopen = { confirmReopen = true },
                )
            }
            if (month.rows.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "Belum ada transaksi",
                        message = "Tidak ada mutasi pada periode ini${if (state.selectedWalletId != null) " untuk dompet terpilih" else ""}.",
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                val groups = month.rows.groupBy { it.item.transaction.occurredAt.toLocalDate() }.toList()
                groups.forEach { (date, rows) ->
                    item(key = "h$date") { DayHeader(date, trailing = dayNetText(rows, state)) }
                    items(rows, key = { it.item.transaction.id }) { row ->
                        StatementRow(row, state.currencyCode, onClick = { onOpenTransaction(row.item.transaction.id) })
                    }
                }
            }
        }
    }

    if (confirmClose) {
        val ended = state.month?.endMillis?.let { it < System.currentTimeMillis() } ?: true
        ConfirmDialog(
            title = "Tutup bulan ${state.periodLabel}?",
            message = "Snapshot permanen dan checksum akan dibuat. Setelah ditutup, transaksi bulan ini tidak bisa diubah " +
                "kecuali bulan dibuka kembali (tercatat di audit log)." +
                if (!ended) "\n\nPeriode ini belum berakhir; transaksi baru pada sisa hari tidak dapat dicatat." else "",
            confirmLabel = "Tutup Bulan",
            onConfirm = { confirmClose = false; onClose() },
            onDismiss = { confirmClose = false },
        )
    }
    if (confirmReopen) {
        ReopenDialog(
            periodLabel = state.periodLabel,
            onConfirm = { reason -> confirmReopen = false; onReopen(reason) },
            onDismiss = { confirmReopen = false },
        )
    }
}

@Composable
private fun ReopenDialog(periodLabel: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buka kembali $periodLabel?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Bulan akan bisa diedit. Saat ditutup lagi, snapshot baru dibuat dengan nomor revisi baru; " +
                        "snapshot lama tetap tersimpan. Pembukaan ini dicatat di audit log.",
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(120) },
                    label = { Text("Alasan (opsional)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(reason.trim()) }) { Text("Buka Kembali") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun MonthNavigator(state: StatementUiState, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Bulan sebelumnya") }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(state.periodLabel, style = MaterialTheme.typography.titleLarge)
            state.month?.let {
                Text(
                    "${DateFormats.date(it.startMillis)} - ${DateFormats.date(it.endMillis)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusBadge(state)
        }
        IconButton(onClick = onNext, enabled = state.canGoNext) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Bulan berikutnya")
        }
    }
}

@Composable
private fun StatusBadge(state: StatementUiState) {
    val closed = state.isClosed
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (closed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (closed) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp),
            )
            Text(state.statusLabel, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun WalletFilter(state: StatementUiState, onSelect: (Long?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { FilterChip(selected = state.selectedWalletId == null, onClick = { onSelect(null) }, label = { Text("Semua dompet") }) }
        items(state.wallets, key = { it.id }) { wallet ->
            FilterChip(selected = state.selectedWalletId == wallet.id, onClick = { onSelect(wallet.id) }, label = { Text(wallet.name) })
        }
    }
}

@Composable
private fun SummaryCard(state: StatementUiState) {
    val month = state.month ?: return
    val s = month.summary
    val cur = state.currencyCode
    Card(
        Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SummaryLine("Saldo awal", MoneyFormatter.format(s.opening, cur))
            if (s.added != 0L) SummaryLine("Dompet baru", MoneyFormatter.format(s.added, cur, showPlusSign = true))
            SummaryLine("Pemasukan", MoneyFormatter.format(s.income, cur, showPlusSign = true), DuitKuTheme.extended.income)
            SummaryLine("Pengeluaran", MoneyFormatter.format(-s.expense, cur), DuitKuTheme.extended.expense)
            if (month.walletId != null && (s.transferIn != 0L || s.transferOut != 0L)) {
                SummaryLine("Transfer masuk", MoneyFormatter.format(s.transferIn, cur, showPlusSign = true), DuitKuTheme.extended.transfer)
                SummaryLine("Transfer keluar", MoneyFormatter.format(-s.transferOut, cur), DuitKuTheme.extended.transfer)
            }
            androidx.compose.material3.HorizontalDivider()
            SummaryLine("Saldo akhir", MoneyFormatter.format(s.closing, cur), bold = true)
            SummaryLine("Net (masuk - keluar)", MoneyFormatter.format(s.net, cur, showPlusSign = true))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (month.reconciliation.isBalanced) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = if (month.reconciliation.isBalanced) DuitKuTheme.extended.income else MaterialTheme.colorScheme.error,
                )
                Text(
                    if (month.reconciliation.isBalanced) "Rekonsiliasi cocok" else "Selisih rekonsiliasi ${MoneyFormatter.format(month.reconciliation.difference, cur)}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun SummaryLine(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    bold: Boolean = false,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
        Text(value, style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun ClosedBanner(state: StatementUiState, onVerify: () -> Unit) {
    val a = state.archive ?: return
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Tertutup - hanya baca", style = MaterialTheme.typography.titleMedium)
            Text(
                "${a.documentNumber} · ${a.revisionLabel} · ${a.closedAt?.let { DateFormats.dateTime(it) }.orEmpty()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SHA-256: ${Checksum.short(a.checksum)}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onVerify) {
                    Icon(Icons.Rounded.VerifiedUser, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text("Verifikasi")
                }
            }
        }
    }
}

@Composable
private fun ActionRow(state: StatementUiState, onExport: () -> Unit, onClose: () -> Unit, onReopen: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = onExport, label = { Text("Export PDF") }, leadingIcon = { Icon(Icons.Rounded.PictureAsPdf, contentDescription = null) })
            AssistChip(onClick = onExport, label = { Text("Export CSV") }, leadingIcon = { Icon(Icons.Rounded.TableChart, contentDescription = null) })
        }
        if (state.isClosed) {
            OutlinedButton(onClick = onReopen, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Buka Kembali Bulan")
            }
        } else {
            Button(onClick = onClose, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(if (state.busy) "Memproses..." else "Tutup Bulan Ini")
            }
        }
    }
}

@Composable
private fun StatementRow(row: MonthRow, currencyCode: String, onClick: () -> Unit) {
    Column {
        TransactionRow(item = row.item, currencyCode = currencyCode, onClick = onClick)
        Text(
            "Saldo: ${MoneyFormatter.format(row.balanceAfter, currencyCode)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth().padding(end = 16.dp, bottom = 4.dp),
        )
    }
}

/** Subtotal harian: pemasukan - pengeluaran hari itu (transfer netral untuk semua dompet). */
private fun dayNetText(rows: List<MonthRow>, state: StatementUiState): String {
    val wallet = state.selectedWalletId
    val net = rows.sumOf { r ->
        val t = r.item.transaction
        if (wallet == null) {
            when (t.type) {
                TransactionType.INCOME -> t.amount
                TransactionType.EXPENSE -> -t.amount
                TransactionType.TRANSFER -> 0L
            }
        } else {
            t.effectOn(wallet)
        }
    }
    return MoneyFormatter.format(net, state.currencyCode, showPlusSign = true)
}

@Preview(showBackground = true)
@Composable
private fun StatementLoadingPreview() {
    DuitKuTheme(dynamicColor = false) {
        StatementContent(
            state = StatementUiState(),
            snackbar = remember { SnackbarHostState() },
            onPrevious = {}, onNext = {}, onSelectWallet = {}, onOpenTransaction = {}, onSearch = {},
            onExport = {}, onAudit = {}, onClose = {}, onReopen = {}, onVerify = {},
        )
    }
}
