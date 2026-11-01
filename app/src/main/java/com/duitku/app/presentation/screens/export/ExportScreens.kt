package com.duitku.app.presentation.screens.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duitku.app.core.util.DateFormats
import com.duitku.app.core.util.toYearMonth
import com.duitku.app.domain.model.ExportFormat
import com.duitku.app.domain.model.ExportKind
import com.duitku.app.domain.model.PdfTheme
import com.duitku.app.domain.repository.ExportRecord
import com.duitku.app.presentation.components.ConfirmDialog
import com.duitku.app.presentation.components.DuitKuTopBar
import com.duitku.app.presentation.components.EmptyState
import com.duitku.app.presentation.components.LoadingBox

private fun periodLabel(ym: Int) = DateFormats.monthShortYear(ym.toYearMonth())

@Composable
fun ExportCenterScreen(
    onBack: () -> Unit,
    onPreview: (Long) -> Unit,
    onHistory: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val result = state.result
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(result?.format?.mimeType ?: "application/pdf"),
    ) { uri -> uri?.let { viewModel.saveTo(it.toString()) } }

    ExportCenterContent(
        state = state, snackbar = snackbar, onBack = onBack, onHistory = onHistory, vm = viewModel,
        onPreview = { result?.let { onPreview(it.historyId) } },
        onShare = { result?.let { ExportActions.share(context, it.fileName, it.format) } },
        onSave = { result?.let { saveLauncher.launch(it.fileName) } },
        onPrint = { result?.let { ExportActions.print(context, it.fileName) } },
        onOpen = { result?.let { ExportActions.openExternally(context, it.fileName, it.format) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExportCenterContent(
    state: ExportUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onHistory: () -> Unit,
    vm: ExportViewModel,
    onPreview: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onPrint: () -> Unit,
    onOpen: () -> Unit,
) {
    Scaffold(
        topBar = {
            DuitKuTopBar(title = "Ekspor & Laporan", onBack = onBack, actions = {
                IconButton(onClick = onHistory) { Icon(Icons.Rounded.History, contentDescription = "Riwayat ekspor") }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.isLoading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Jenis laporan", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportKind.entries.forEach { k -> FilterChip(selected = state.kind == k, onClick = { vm.setKind(k) }, label = { Text(k.label) }) }
            }

            Text("Format", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportFormat.entries.forEach { f ->
                    FilterChip(selected = state.format == f, onClick = { vm.setFormat(f) }, label = { Text(f.name) })
                }
            }

            Text(if (state.kind == ExportKind.MONTHLY) "Bulan" else "Dari bulan", style = MaterialTheme.typography.labelLarge)
            PeriodRow(state.periods, state.fromYm, vm::setFrom)
            if (state.kind != ExportKind.MONTHLY) {
                Text("Sampai bulan", style = MaterialTheme.typography.labelLarge)
                PeriodRow(state.periods, state.toYm, vm::setTo)
            }

            if (state.canChooseWallet) {
                Text("Dompet", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.walletId == null, onClick = { vm.setWallet(null) }, label = { Text("Semua dompet") })
                    state.wallets.forEach { w -> FilterChip(selected = state.walletId == w.id, onClick = { vm.setWallet(w.id) }, label = { Text(w.name) }) }
                }
            }

            if (state.isPdf) {
                Text("Tema PDF", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PdfTheme.entries.forEach { t -> FilterChip(selected = state.theme == t, onClick = { vm.setTheme(t) }, label = { Text(t.label) }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sertakan grafik", Modifier.weight(1f))
                    Switch(checked = state.includeCharts, onCheckedChange = vm::setCharts)
                }
                if (state.kind == ExportKind.MONTHLY) {
                    OutlinedTextField(
                        value = state.note, onValueChange = vm::setNote, label = { Text("Catatan bulan (dicetak di halaman penutup)") },
                        modifier = Modifier.fillMaxWidth(), maxLines = 3,
                    )
                    Text("Saldo sesuai rekening bank (opsional, untuk rekonsiliasi)", style = MaterialTheme.typography.labelLarge)
                    state.wallets.filter { state.walletId == null || it.id == state.walletId }.forEach { w ->
                        OutlinedTextField(
                            value = state.bankInputs[w.id].orEmpty(), onValueChange = { vm.setBank(w.id, it) },
                            label = { Text(w.name) }, prefix = { Text("Rp ") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            state.progress?.let { p ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${p.label} (${p.percent}%)", style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(progress = { p.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(onClick = vm::start, enabled = !state.isRunning && state.periods.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(if (state.isRunning) "Membuat..." else "Buat ${state.format.name}")
            }

            state.result?.let { r ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Berkas siap", style = MaterialTheme.typography.titleMedium)
                        Text("${r.fileName} · ${r.sizeBytes / 1024} KB · ${if (r.isFinal) "Final" else "Draft"}", style = MaterialTheme.typography.bodyMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (r.format == ExportFormat.PDF) {
                                AssistChip(onClick = onPreview, label = { Text("Pratinjau") })
                                AssistChip(onClick = onPrint, label = { Text("Cetak") })
                            } else {
                                AssistChip(onClick = onOpen, label = { Text("Buka") })
                            }
                            AssistChip(onClick = onShare, label = { Text("Bagikan") })
                            AssistChip(onClick = onSave, label = { Text("Simpan ke...") })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodRow(periods: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(periods.asReversed(), key = { it }) { ym ->
            FilterChip(selected = ym == selected, onClick = { onSelect(ym) }, label = { Text(periodLabel(ym)) })
        }
    }
}

// ---------- Riwayat ----------

@Composable
fun ExportHistoryScreen(
    onBack: () -> Unit,
    onPreview: (Long) -> Unit,
    viewModel: ExportHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var pending by remember { mutableStateOf<ExportRecord?>(null) }
    var toDelete by remember { mutableStateOf<ExportRecord?>(null) }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(pending?.format?.mimeType ?: "application/pdf"),
    ) { uri -> uri?.let { u -> pending?.let { viewModel.saveTo(it.id, u.toString()) } } }

    Scaffold(topBar = { DuitKuTopBar(title = "Riwayat Ekspor", onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        when {
            state.isLoading -> LoadingBox(Modifier.padding(padding))
            state.items.isEmpty() -> EmptyState(
                title = "Belum ada ekspor", message = "PDF, CSV, dan Excel yang pernah dibuat tersimpan di sini dan bisa dibuka ulang.",
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.items, key = { it.id }) { r ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.fileName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${r.kind.label} · ${periodLabel(r.fromPeriodYm)}" + (if (r.toPeriodYm != r.fromPeriodYm) " - ${periodLabel(r.toPeriodYm)}" else "") +
                                            " · ${r.sizeBytes / 1024} KB",
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text("Dibuat ${DateFormats.dateTime(r.createdAt)}" + (r.documentNumber?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                }
                                IconButton(onClick = { toDelete = r }) { Icon(Icons.Rounded.Delete, contentDescription = "Hapus") }
                            }
                            if (!r.fileExists) {
                                Text("Berkas sudah tidak ada di perangkat", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                            } else {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (r.format == ExportFormat.PDF) {
                                        AssistChip(onClick = { onPreview(r.id) }, label = { Text("Buka") })
                                        AssistChip(onClick = { ExportActions.print(context, r.fileName) }, label = { Text("Cetak") })
                                    } else {
                                        AssistChip(onClick = { ExportActions.openExternally(context, r.fileName, r.format) }, label = { Text("Buka") })
                                    }
                                    AssistChip(onClick = { ExportActions.share(context, r.fileName, r.format) }, label = { Text("Bagikan") })
                                    AssistChip(onClick = { pending = r; saveLauncher.launch(r.fileName) }, label = { Text("Simpan ke...") })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    toDelete?.let { r ->
        ConfirmDialog(
            title = "Hapus berkas?", message = "${r.fileName} akan dihapus dari perangkat. Arsip mutasi bulanan tidak terpengaruh.",
            confirmLabel = "Hapus", destructive = true,
            onConfirm = { toDelete = null; viewModel.delete(r) }, onDismiss = { toDelete = null },
        )
    }
}
