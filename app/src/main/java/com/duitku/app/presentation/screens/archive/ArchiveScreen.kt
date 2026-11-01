package com.duitku.app.presentation.screens.archive

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
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duitku.app.core.util.DateFormats
import com.duitku.app.core.util.MoneyFormatter
import com.duitku.app.core.util.toYearMonth
import com.duitku.app.domain.model.AuditEntry
import com.duitku.app.domain.model.StatementStatus
import com.duitku.app.domain.model.StatementSummary
import com.duitku.app.domain.usecase.statement.Checksum
import com.duitku.app.presentation.components.DuitKuTopBar
import com.duitku.app.presentation.components.EmptyState
import com.duitku.app.presentation.components.LoadingBox
import com.duitku.app.presentation.theme.DuitKuTheme

@Composable
fun ArchiveScreen(
    onBack: () -> Unit,
    onView: (periodYm: Int) -> Unit,
    onExport: (periodYm: Int) -> Unit,
    onShare: (periodYm: Int) -> Unit,
    viewModel: ArchiveViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    ArchiveContent(
        state = state, snackbar = snackbar, onBack = onBack, onQueryChange = viewModel::onQueryChange,
        onSelectYear = viewModel::selectYear, onView = onView, onExport = onExport, onShare = onShare,
        onVerify = viewModel::verify,
    )
}

@Composable
fun ArchiveContent(
    state: ArchiveUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectYear: (Int?) -> Unit,
    onView: (Int) -> Unit,
    onExport: (Int) -> Unit,
    onShare: (Int) -> Unit,
    onVerify: (Long) -> Unit,
) {
    Scaffold(
        topBar = { DuitKuTopBar(title = "Arsip Mutasi", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = { Text("Cari bulan atau nomor dokumen") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (state.years.isNotEmpty()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.years) { y ->
                        FilterChip(selected = state.year == y, onClick = { onSelectYear(y) }, label = { Text("$y") })
                    }
                }
            }
            when {
                state.isLoading -> LoadingBox()
                state.totalCount == 0 -> EmptyState(
                    title = "Belum ada arsip",
                    message = "Tutup bulan dari layar Mutasi untuk menyimpan salinan permanen mutasi bulan tersebut.",
                    icon = Icons.Rounded.Inventory2,
                )
                state.items.isEmpty() -> EmptyState(title = "Tidak ada hasil", message = "Ubah kata kunci atau tahun.")
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.byYear.forEach { (year, list) ->
                        item(key = "y$year") { Text("$year", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) }
                        items(list, key = { it.id }) { s ->
                            ArchiveItem(s, state.currencyCode, onView, onExport, onShare, onVerify)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveItem(
    s: StatementSummary,
    currency: String,
    onView: (Int) -> Unit,
    onExport: (Int) -> Unit,
    onShare: (Int) -> Unit,
    onVerify: (Long) -> Unit,
) {
    val closed = s.status == StatementStatus.CLOSED
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(DateFormats.monthYear(s.periodYm.toYearMonth()), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${s.documentNumber} · ${s.revisionLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (closed) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    contentDescription = if (closed) "Tertutup" else "Dibuka kembali",
                    tint = if (closed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                )
            }
            Text(
                "Saldo akhir ${MoneyFormatter.format(s.closingBalance, currency)} · ${s.transactionCount} transaksi",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                (if (closed) "Tertutup" else "Dibuka kembali (draf)") + " · SHA-256 ${Checksum.short(s.checksum)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { onView(s.periodYm) }, label = { Text("Lihat") })
                AssistChip(onClick = { onExport(s.periodYm) }, label = { Text("Export PDF") })
                AssistChip(onClick = { onShare(s.periodYm) }, label = { Text("Bagikan") })
                if (closed) AssistChip(onClick = { onVerify(s.id) }, label = { Text("Verifikasi") })
            }
        }
    }
}

// ---------- Audit log ----------

@Composable
fun AuditLogScreen(onBack: () -> Unit, viewModel: AuditLogViewModel = hiltViewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    AuditLogContent(entries, onBack)
}

@Composable
fun AuditLogContent(entries: List<AuditEntry>?, onBack: () -> Unit) {
    Scaffold(topBar = { DuitKuTopBar(title = "Audit Log", onBack = onBack) }) { padding ->
        when {
            entries == null -> LoadingBox(Modifier.padding(padding))
            entries.isEmpty() -> EmptyState(
                title = "Belum ada catatan",
                message = "Penutupan bulan, pembukaan kembali, dan perubahan pada bulan yang diarsipkan akan tercatat di sini.",
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries, key = { it.id }) { e ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${e.action} · ${e.entityType}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(e.description, style = MaterialTheme.typography.bodyMedium)
                            e.oldValue?.let { Text("Sebelum: $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            e.newValue?.let { Text("Sesudah: $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Text(
                                DateFormats.dateTime(e.createdAt) + (e.periodYm?.let { " · periode " + DateFormats.monthYear(it.toYearMonth()) } ?: ""),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ArchiveEmptyPreview() {
    DuitKuTheme(dynamicColor = false) {
        ArchiveContent(
            state = ArchiveUiState(isLoading = false),
            snackbar = remember { SnackbarHostState() },
            onBack = {}, onQueryChange = {}, onSelectYear = {}, onView = {}, onExport = {}, onShare = {}, onVerify = {},
        )
    }
}
