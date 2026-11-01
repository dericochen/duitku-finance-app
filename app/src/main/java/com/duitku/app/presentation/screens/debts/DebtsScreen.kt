package com.duitku.app.presentation.screens.debts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duitku.app.core.util.DateFormats
import com.duitku.app.core.util.MoneyFormatter
import com.duitku.app.domain.model.Debt
import com.duitku.app.domain.model.DebtKind
import com.duitku.app.domain.model.DebtState
import com.duitku.app.domain.model.DebtWithPaid
import com.duitku.app.presentation.components.ConfirmDialog
import com.duitku.app.presentation.components.DatePickerDialogLocal
import com.duitku.app.presentation.components.DuitKuTopBar
import com.duitku.app.presentation.components.EmptyState
import com.duitku.app.presentation.components.LoadingBox
import com.duitku.app.presentation.theme.DuitKuTheme
import java.time.LocalDate

@Composable
fun DebtsScreen(onBack: () -> Unit, viewModel: DebtsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    DebtsContent(state, snackbar, onBack, viewModel)
}

@Composable
private fun DebtsContent(state: DebtsUiState, snackbar: SnackbarHostState, onBack: () -> Unit, vm: DebtsViewModel) {
    Scaffold(
        topBar = { DuitKuTopBar(title = "Utang & Piutang", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = vm::startAdd, icon = { Icon(Icons.Rounded.Add, contentDescription = null) }, text = { Text("Tambah") })
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            PrimaryTabRow(selectedTabIndex = DebtKind.entries.indexOf(state.kind)) {
                DebtKind.entries.forEach { k -> Tab(selected = state.kind == k, onClick = { vm.selectKind(k) }, text = { Text(k.label) }) }
            }
            when {
                state.isLoading -> LoadingBox()
                state.items.isEmpty() -> EmptyState(
                    title = "Belum ada catatan ${state.kind.label.lowercase()}",
                    message = if (state.kind == DebtKind.DEBT) "Catat uang yang Anda pinjam beserta jatuh tempo agar tidak terlewat." else "Catat uang yang dipinjam orang lain dari Anda.",
                    actionLabel = "Tambah", onAction = vm::startAdd,
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "total") {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Total ${state.kind.label.lowercase()} yang belum lunas", style = MaterialTheme.typography.labelLarge)
                                Text(MoneyFormatter.format(state.totalRemaining, state.currencyCode), style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                    }
                    items(state.items, key = { it.item.debt.id }) { d -> DebtCard(d, state.currencyCode) { vm.openDetail(d.item) } }
                }
            }
        }
    }
    state.form?.let { DebtFormDialog(it, vm) }
    state.payment?.let { PaymentDialog(it, state.currencyCode, vm) }
    state.detail?.let { DebtDetailDialog(it, state, vm) }
}

@Composable
private fun stateColor(state: DebtState): Color = when (state) {
    DebtState.PAID -> DuitKuTheme.extended.income
    DebtState.OVERDUE -> DuitKuTheme.extended.expense
    DebtState.DUE_SOON -> DuitKuTheme.extended.warning
    DebtState.ACTIVE -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun DebtCard(d: DebtItem, currency: String, onClick: () -> Unit) {
    val debt = d.item.debt
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(debt.partyName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(d.state.label, style = MaterialTheme.typography.labelLarge, color = stateColor(d.state))
            }
            Text(
                "Sisa ${MoneyFormatter.format(d.item.remaining, currency)} dari ${MoneyFormatter.format(debt.principalAmount, currency)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(
                progress = { d.item.percent / 100f }, color = stateColor(d.state).takeIf { d.state != DebtState.ACTIVE } ?: MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small),
            )
            debt.dueDate?.let {
                Text("Jatuh tempo ${DateFormats.date(it)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DebtFormDialog(form: DebtFormState, vm: DebtsViewModel) {
    var pickDate by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = vm::dismissForm,
        title = { Text((if (form.id > 0) "Ubah " else "Tambah ") + form.kind.label.lowercase()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = form.party, onValueChange = vm::onParty, singleLine = true,
                    label = { Text(if (form.kind == DebtKind.DEBT) "Berutang kepada" else "Dipinjam oleh") },
                )
                OutlinedTextField(
                    value = form.amountInput, onValueChange = vm::onAmount, label = { Text("Nominal") }, prefix = { Text("Rp ") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { pickDate = true }, label = { Text(form.due?.let { "Jatuh tempo ${DateFormats.date(it)}" } ?: "Tanpa jatuh tempo") })
                    if (form.due != null) AssistChip(onClick = { vm.onDue(null) }, label = { Text("Hapus") })
                }
                OutlinedTextField(value = form.note, onValueChange = vm::onNote, label = { Text("Catatan (opsional)") }, maxLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ingatkan saat jatuh tempo", Modifier.weight(1f))
                    Switch(checked = form.reminder, onCheckedChange = vm::onReminder)
                }
                form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = vm::saveForm) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = vm::dismissForm) { Text("Batal") } },
    )
    if (pickDate) {
        DatePickerDialogLocal(
            initial = form.due ?: LocalDate.now().plusDays(14),
            onConfirm = { vm.onDue(it); pickDate = false }, onDismiss = { pickDate = false },
        )
    }
}

@Composable
private fun PaymentDialog(form: PaymentFormState, currency: String, vm: DebtsViewModel) {
    AlertDialog(
        onDismissRequest = vm::dismissPayment,
        title = { Text("Catat pembayaran") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${form.debt.debt.partyName} · sisa ${MoneyFormatter.format(form.debt.remaining, currency)}")
                OutlinedTextField(
                    value = form.amountInput, onValueChange = vm::onPaymentAmount, label = { Text("Nominal dibayar") }, prefix = { Text("Rp ") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = form.error != null, supportingText = form.error?.let { { Text(it) } },
                )
                OutlinedTextField(value = form.note, onValueChange = vm::onPaymentNote, label = { Text("Catatan (opsional)") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = vm::savePayment) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = vm::dismissPayment) { Text("Batal") } },
    )
}

@Composable
private fun DebtDetailDialog(item: DebtWithPaid, state: DebtsUiState, vm: DebtsViewModel) {
    var confirmDelete by remember { mutableStateOf(false) }
    val debt: Debt = item.debt
    AlertDialog(
        onDismissRequest = vm::closeDetail,
        title = { Text(debt.partyName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${debt.kind.label} ${MoneyFormatter.format(debt.principalAmount, state.currencyCode)} · terbayar ${MoneyFormatter.format(item.paidAmount, state.currencyCode)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                debt.dueDate?.let { Text("Jatuh tempo ${DateFormats.date(it)}", style = MaterialTheme.typography.bodyMedium) }
                if (debt.note.isNotBlank()) Text(debt.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Riwayat pembayaran", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
                if (state.detailPayments.isEmpty()) Text("Belum ada pembayaran.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.detailPayments.take(10).forEach { p ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(DateFormats.date(p.paidAt) + if (p.note.isNotBlank()) " · ${p.note}" else "", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(MoneyFormatter.format(p.amount, state.currencyCode), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!debt.isPaidOff) {
                        AssistChip(onClick = { vm.closeDetail(); vm.startPayment(item) }, label = { Text("Bayar") })
                        AssistChip(onClick = { vm.markPaidOff(debt) }, label = { Text("Tandai lunas") })
                    }
                    AssistChip(onClick = { vm.closeDetail(); vm.startEdit(debt) }, label = { Text("Ubah") })
                }
            }
        },
        confirmButton = { TextButton(onClick = vm::closeDetail) { Text("Tutup") } },
        dismissButton = { TextButton(onClick = { confirmDelete = true }) { Text("Hapus", color = MaterialTheme.colorScheme.error) } },
    )
    if (confirmDelete) {
        ConfirmDialog(
            title = "Hapus catatan?", message = "Catatan beserta riwayat pembayarannya akan dihapus.", confirmLabel = "Hapus", destructive = true,
            onConfirm = { confirmDelete = false; vm.delete(debt) }, onDismiss = { confirmDelete = false },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DebtCardPreview() {
    val debt = Debt(1, DebtKind.DEBT, "Budi", 2_000_000, System.currentTimeMillis() - 86_400_000)
    DuitKuTheme(dynamicColor = false) {
        DebtCard(DebtItem(DebtWithPaid(debt, 500_000), DebtState.OVERDUE), "IDR") {}
    }
}
