package com.duitku.app.presentation.screens.budget

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duitku.app.core.util.MoneyFormatter
import com.duitku.app.domain.model.Budget
import com.duitku.app.domain.model.BudgetLine
import com.duitku.app.presentation.components.BudgetProgressBar
import com.duitku.app.presentation.components.EmptyState
import com.duitku.app.presentation.components.IconBadge
import com.duitku.app.presentation.components.LoadingBox
import com.duitku.app.presentation.components.budgetColor
import com.duitku.app.presentation.theme.DuitKuTheme

@Composable
fun BudgetScreen(modifier: Modifier = Modifier, viewModel: BudgetViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    BudgetContent(
        state = state, snackbar = snackbar, modifier = modifier,
        onPrevious = viewModel::previous, onNext = viewModel::next, onAdd = viewModel::startAdd,
        onEdit = viewModel::startEdit, onCopy = viewModel::copyFromPrevious,
        onDismissEdit = viewModel::dismissEdit, onCategoryChange = viewModel::onCategoryChange,
        onAmountChange = viewModel::onAmountChange, onRolloverChange = viewModel::onRolloverChange,
        onSave = viewModel::save, onDelete = viewModel::delete,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetContent(
    state: BudgetUiState,
    snackbar: SnackbarHostState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (BudgetItem) -> Unit,
    onCopy: () -> Unit,
    onDismissEdit: () -> Unit,
    onCategoryChange: (Long?) -> Unit,
    onAmountChange: (String) -> Unit,
    onRolloverChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Anggaran") },
                actions = {
                    IconButton(onClick = onCopy) { Icon(Icons.Rounded.ContentCopy, contentDescription = "Salin dari bulan sebelumnya") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Tambah anggaran") },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "nav") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrevious) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Bulan sebelumnya") }
                    Text(state.periodLabel, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(onClick = onNext, enabled = state.canGoNext) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Bulan berikutnya") }
                }
            }
            if (state.items.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "Belum ada anggaran",
                        message = "Atur anggaran total bulanan atau per kategori. Anda akan diberi tahu saat pemakaian mencapai 80% dan 100%.",
                        actionLabel = "Tambah anggaran",
                        onAction = onAdd,
                        modifier = Modifier.padding(vertical = 32.dp),
                    )
                }
            } else {
                state.total?.let { total -> item(key = "total") { BudgetCard(total, state.currencyCode, highlight = true) { onEdit(total) } } }
                items(state.byCategory, key = { it.line.budget.id }) { item ->
                    BudgetCard(item, state.currencyCode) { onEdit(item) }
                }
            }
        }
    }

    state.edit?.let { edit ->
        BudgetEditDialog(state, edit, onDismissEdit, onCategoryChange, onAmountChange, onRolloverChange, onSave, onDelete)
    }
}

@Composable
private fun BudgetCard(item: BudgetItem, currency: String, highlight: Boolean = false, onClick: () -> Unit) {
    val line = item.line
    val color = budgetColor(line.status)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (highlight) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(item.iconKey, item.colorArgb)
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${MoneyFormatter.format(line.spent, currency)} / ${MoneyFormatter.format(line.limit, currency)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${line.percent}%", style = MaterialTheme.typography.titleMedium, color = color)
                    Text(line.status.label, style = MaterialTheme.typography.labelMedium, color = color)
                }
            }
            BudgetProgressBar(line.percent)
            Text(
                if (line.remaining >= 0) "Sisa ${MoneyFormatter.format(line.remaining, currency)}" else "Melebihi ${MoneyFormatter.format(-line.remaining, currency)}",
                style = MaterialTheme.typography.bodyMedium,
                color = color,
            )
            if (line.rollIn > 0) {
                Text("Termasuk sisa bulan lalu ${MoneyFormatter.format(line.rollIn, currency)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (line.budget.rollover) {
                Text("Sisa dibawa ke bulan berikutnya", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetEditDialog(
    state: BudgetUiState,
    edit: BudgetEditState,
    onDismiss: () -> Unit,
    onCategoryChange: (Long?) -> Unit,
    onAmountChange: (String) -> Unit,
    onRolloverChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (edit.existingId > 0) "Ubah anggaran" else "Tambah anggaran") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!edit.categoryLocked) {
                    Text("Untuk", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!state.hasTotalBudget) {
                            FilterChip(selected = edit.categoryId == null, onClick = { onCategoryChange(null) }, label = { Text("Total bulanan") })
                        }
                        state.unbudgetedCategories.forEach { c ->
                            FilterChip(selected = edit.categoryId == c.id, onClick = { onCategoryChange(c.id) }, label = { Text(c.name) })
                        }
                    }
                } else {
                    Text(
                        if (edit.categoryId == null) "Total bulanan" else state.categories.firstOrNull { it.id == edit.categoryId }?.name ?: "Kategori",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                OutlinedTextField(
                    value = edit.amountInput,
                    onValueChange = onAmountChange,
                    label = { Text("Nominal anggaran") },
                    prefix = { Text("Rp ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = edit.error != null,
                    supportingText = edit.error?.let { { Text(it) } },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Bawa sisa ke bulan depan", style = MaterialTheme.typography.bodyLarge)
                        Text("Sisa anggaran menambah batas bulan berikutnya", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = edit.rollover, onCheckedChange = onRolloverChange)
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Simpan") } },
        dismissButton = {
            Row {
                if (edit.existingId > 0) TextButton(onClick = onDelete) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Batal") }
            }
        },
    )
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun BudgetPreview() {
    fun item(id: Long, cat: Long?, name: String, amount: Long, spent: Long) = BudgetItem(
        BudgetLine(Budget(id, 202611, cat, amount, rollover = true), rollIn = 0, spent = spent), name, "food", 0xFFE65100.toInt(),
    )
    DuitKuTheme(dynamicColor = false) {
        BudgetContent(
            state = BudgetUiState(
                isLoading = false, periodYm = 202611, currentPeriodYm = 202611,
                items = listOf(item(1, null, "Total bulanan", 7_000_000, 4_000_000), item(2, 1, "Makanan", 2_000_000, 1_700_000), item(3, 2, "Transport", 500_000, 600_000)),
            ),
            snackbar = remember { SnackbarHostState() },
            onPrevious = {}, onNext = {}, onAdd = {}, onEdit = {}, onCopy = {}, onDismissEdit = {},
            onCategoryChange = {}, onAmountChange = {}, onRolloverChange = {}, onSave = {}, onDelete = {},
        )
    }
}
