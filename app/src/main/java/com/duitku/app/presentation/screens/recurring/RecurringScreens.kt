package com.duitku.app.presentation.screens.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duitku.app.core.util.DateFormats
import com.duitku.app.core.util.MoneyFormatter
import com.duitku.app.domain.model.RecurrenceFrequency
import com.duitku.app.domain.model.RecurringTransaction
import com.duitku.app.domain.model.TransactionType
import com.duitku.app.presentation.common.UiEvent
import com.duitku.app.presentation.components.ConfirmDialog
import com.duitku.app.presentation.components.DatePickerDialogLocal
import com.duitku.app.presentation.components.DuitKuTopBar
import com.duitku.app.presentation.components.EmptyState
import com.duitku.app.presentation.components.LoadingBox
import com.duitku.app.presentation.components.amountColor
import com.duitku.app.presentation.theme.DuitKuTheme

@Composable
fun RecurringListScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: RecurringListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RecurringListContent(state, onBack, onAdd, onEdit, viewModel::toggle, viewModel::delete)
}

@Composable
fun RecurringListContent(
    state: RecurringListUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onToggle: (RecurringTransaction) -> Unit,
    onDelete: (RecurringTransaction) -> Unit,
) {
    var toDelete by remember { mutableStateOf<RecurringTransaction?>(null) }
    Scaffold(
        topBar = { DuitKuTopBar(title = "Transaksi Berulang", onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Rounded.Add, contentDescription = null) }, text = { Text("Tambah") })
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingBox(Modifier.padding(padding))
            state.items.isEmpty() -> EmptyState(
                title = "Belum ada transaksi berulang",
                message = "Atur gaji, cicilan, atau langganan agar tercatat otomatis. Yang terlewat saat ponsel mati akan dicatat saat aktif lagi.",
                modifier = Modifier.padding(padding), actionLabel = "Tambah", onAction = onAdd,
            )
            else -> LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "burden") {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Perkiraan beban pengeluaran rutin per bulan", style = MaterialTheme.typography.labelLarge)
                            Text(MoneyFormatter.format(state.monthlyBurden, state.currencyCode), style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
                items(state.items, key = { it.rule.id }) { item ->
                    val r = item.rule
                    Card(onClick = { onEdit(r.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(r.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    MoneyFormatter.format(r.amount, state.currencyCode),
                                    style = MaterialTheme.typography.bodyLarge, color = amountColor(r.type),
                                )
                                Text(
                                    "${r.frequencyLabel} · ${item.walletName}" + (item.categoryName?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    if (r.isActive) "Berikutnya ${DateFormats.date(r.nextDueAt)}" else "Dijeda",
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline,
                                )
                            }
                            Switch(checked = r.isActive, onCheckedChange = { onToggle(r) })
                            IconButton(onClick = { toDelete = r }) { Icon(Icons.Rounded.Delete, contentDescription = "Hapus ${r.title}") }
                        }
                    }
                }
            }
        }
    }
    toDelete?.let { rule ->
        ConfirmDialog(
            title = "Hapus transaksi berulang?",
            message = "\"${rule.title}\" tidak akan dicatat lagi. Transaksi yang sudah tercatat tetap ada.",
            confirmLabel = "Hapus", destructive = true,
            onConfirm = { toDelete = null; onDelete(rule) }, onDismiss = { toDelete = null },
        )
    }
}

@Composable
fun RecurringEditScreen(onBack: () -> Unit, viewModel: RecurringEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is UiEvent.ShowMessage -> snackbar.showSnackbar(e.text)
                UiEvent.NavigateBack -> onBack()
            }
        }
    }
    RecurringEditContent(state, snackbar, onBack, viewModel)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecurringEditContent(
    state: RecurringEditUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    vm: RecurringEditViewModel,
) {
    var pick by remember { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = { DuitKuTopBar(title = if (state.isEdit) "Ubah Transaksi Berulang" else "Transaksi Berulang Baru", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.isLoading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.title, onValueChange = vm::onTitle, label = { Text("Nama (mis. Gaji, Cicilan motor)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TransactionType.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = state.type == t, onClick = { vm.onType(t) },
                        shape = SegmentedButtonDefaults.itemShape(i, TransactionType.entries.size),
                    ) { Text(t.label) }
                }
            }
            OutlinedTextField(
                value = state.amountInput, onValueChange = vm::onAmount, label = { Text("Nominal") }, prefix = { Text("Rp ") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
            )

            Text(if (state.type == TransactionType.TRANSFER) "Dari dompet" else "Dompet", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.wallets.forEach { w -> FilterChip(selected = state.walletId == w.id, onClick = { vm.onWallet(w.id) }, label = { Text(w.name) }) }
            }
            if (state.type == TransactionType.TRANSFER) {
                Text("Ke dompet", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.wallets.forEach { w ->
                        FilterChip(selected = state.toWalletId == w.id, onClick = { vm.onToWallet(w.id) }, enabled = w.id != state.walletId, label = { Text(w.name) })
                    }
                }
            } else {
                Text("Kategori", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.categoryTree.forEach { n ->
                        FilterChip(selected = state.categoryId == n.category.id, onClick = { vm.onCategory(n.category.id) }, label = { Text(n.category.name) })
                    }
                }
                state.categoryTree.firstOrNull { it.category.id == state.categoryId }?.children?.takeIf { it.isNotEmpty() }?.let { subs ->
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        subs.forEach { c ->
                            FilterChip(
                                selected = state.subCategoryId == c.id,
                                onClick = { vm.onSubCategory(if (state.subCategoryId == c.id) null else c.id) },
                                label = { Text(c.name) },
                            )
                        }
                    }
                }
            }

            Text("Frekuensi", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecurrenceFrequency.entries.forEach { f -> FilterChip(selected = state.frequency == f, onClick = { vm.onFrequency(f) }, label = { Text(f.label) }) }
            }
            OutlinedTextField(
                value = state.intervalInput, onValueChange = vm::onInterval, label = { Text("Setiap berapa kali (interval)") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { pick = "start" }, label = { Text("Mulai ${DateFormats.date(state.startDate)}") })
                AssistChip(onClick = { pick = "end" }, label = { Text(state.endDate?.let { "Berakhir ${DateFormats.date(it)}" } ?: "Tanpa tanggal berakhir") })
                if (state.endDate != null) AssistChip(onClick = { vm.onEndDate(null) }, label = { Text("Hapus") })
            }
            OutlinedTextField(
                value = state.note, onValueChange = vm::onNote, label = { Text("Catatan (opsional)") }, modifier = Modifier.fillMaxWidth(), maxLines = 2,
            )
            Text(
                "Transaksi dicatat otomatis pukul 08.00. Jadwal yang terlewat saat ponsel mati akan dicatat saat aplikasi aktif kembali.",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = vm::save, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.isSaving) "Menyimpan..." else "Simpan")
            }
        }
    }
    pick?.let { which ->
        DatePickerDialogLocal(
            initial = (if (which == "start") state.startDate else state.endDate) ?: state.startDate,
            onConfirm = { d -> pick = null; if (which == "start") vm.onStartDate(d) else vm.onEndDate(d) },
            onDismiss = { pick = null },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RecurringListPreview() {
    val rule = RecurringTransaction(
        id = 1, title = "Gaji", type = TransactionType.INCOME, amount = 8_500_000, walletId = 1, categoryId = 1,
        frequency = RecurrenceFrequency.MONTHLY, startAt = 0, nextDueAt = System.currentTimeMillis(),
    )
    DuitKuTheme(dynamicColor = false) {
        RecurringListContent(
            RecurringListUiState(isLoading = false, items = listOf(RecurringItem(rule, "Bank", "Gaji")), monthlyBurden = 1_200_000),
            onBack = {}, onAdd = {}, onEdit = {}, onToggle = {}, onDelete = {},
        )
    }
}
