package com.duitku.app.presentation.screens.goals

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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.duitku.app.domain.model.GoalEstimate
import com.duitku.app.domain.model.SavingGoal
import com.duitku.app.presentation.components.ColorPicker
import com.duitku.app.presentation.components.ConfirmDialog
import com.duitku.app.presentation.components.DatePickerDialogLocal
import com.duitku.app.presentation.components.DuitKuTopBar
import com.duitku.app.presentation.components.EmptyState
import com.duitku.app.presentation.components.IconBadge
import com.duitku.app.presentation.components.LoadingBox
import com.duitku.app.presentation.theme.DuitKuTheme
import java.time.LocalDate

@Composable
fun GoalsScreen(onBack: () -> Unit, viewModel: GoalsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    GoalsContent(state, snackbar, onBack, viewModel)
}

@Composable
private fun GoalsContent(state: GoalsUiState, snackbar: SnackbarHostState, onBack: () -> Unit, vm: GoalsViewModel) {
    Scaffold(
        topBar = { DuitKuTopBar(title = "Target Tabungan", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = vm::startAdd, icon = { Icon(Icons.Rounded.Add, contentDescription = null) }, text = { Text("Target baru") })
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingBox(Modifier.padding(padding))
            state.items.isEmpty() -> EmptyState(
                title = "Belum ada target tabungan",
                message = "Buat target (dana darurat, liburan, gadget) lalu tambahkan setoran secara berkala.",
                modifier = Modifier.padding(padding), actionLabel = "Buat target", onAction = vm::startAdd,
            )
            else -> LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.items, key = { it.goal.id }) { item ->
                    GoalCard(item.goal, item.estimate, state.currencyCode, onOpen = { vm.openDetail(item.goal) }, onAdd = { vm.startContribution(item.goal) })
                }
            }
        }
    }

    state.form?.let { GoalFormDialog(it, vm) }
    state.contribution?.let { ContributionDialog(it, state.currencyCode, vm) }
    state.detail?.let { goal -> GoalDetailDialog(goal, state, vm) }
}

@Composable
private fun GoalCard(goal: SavingGoal, estimate: GoalEstimate, currency: String, onOpen: () -> Unit, onAdd: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(goal.iconKey, goal.colorArgb)
                Column(Modifier.weight(1f)) {
                    Text(goal.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${MoneyFormatter.format(goal.savedAmount, currency)} / ${MoneyFormatter.format(goal.targetAmount, currency)}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (goal.isCompleted) Icon(Icons.Rounded.CheckCircle, contentDescription = "Tercapai", tint = DuitKuTheme.extended.income)
                else Text("${goal.percent}%", style = MaterialTheme.typography.titleMedium, color = Color(goal.colorArgb))
            }
            LinearProgressIndicator(
                progress = { goal.percent / 100f }, color = Color(goal.colorArgb),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(MaterialTheme.shapes.small),
            )
            if (goal.isCompleted) {
                Text("Target tercapai", style = MaterialTheme.typography.bodyMedium, color = DuitKuTheme.extended.income)
            } else {
                estimate.estimatedDate?.let { Text("Perkiraan tercapai ${DateFormats.date(it)}", style = MaterialTheme.typography.bodyMedium) }
                    ?: Text("Belum ada setoran 90 hari terakhir, perkiraan belum tersedia", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                goal.deadline?.let { d ->
                    val status = when (estimate.onTrack) {
                        true -> " (sesuai jalur)"
                        false -> " (terlambat dari jalur)"
                        null -> ""
                    }
                    Text("Tenggat ${DateFormats.date(d)}$status", style = MaterialTheme.typography.labelMedium,
                        color = if (estimate.onTrack == false) DuitKuTheme.extended.expense else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                estimate.neededPerMonth?.let {
                    Text("Perlu ${MoneyFormatter.format(it, currency)} per bulan agar tepat waktu", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AssistChip(onClick = onAdd, label = { Text("Tambah setoran") })
            }
        }
    }
}

@Composable
private fun GoalFormDialog(form: GoalFormState, vm: GoalsViewModel) {
    var pickDate by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = vm::dismissForm,
        title = { Text(if (form.id > 0) "Ubah target" else "Target baru") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = form.name, onValueChange = vm::onName, label = { Text("Nama target") }, singleLine = true)
                OutlinedTextField(
                    value = form.targetInput, onValueChange = vm::onTarget, label = { Text("Nominal target") }, prefix = { Text("Rp ") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { pickDate = true }, label = { Text(form.deadline?.let { "Tenggat ${DateFormats.date(it)}" } ?: "Tanpa tenggat") })
                    if (form.deadline != null) AssistChip(onClick = { vm.onDeadline(null) }, label = { Text("Hapus") })
                }
                ColorPicker(selectedArgb = form.colorArgb, onSelected = vm::onColor)
                form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = vm::saveForm) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = vm::dismissForm) { Text("Batal") } },
    )
    if (pickDate) {
        DatePickerDialogLocal(
            initial = form.deadline ?: LocalDate.now().plusMonths(3),
            onConfirm = { vm.onDeadline(it); pickDate = false }, onDismiss = { pickDate = false },
        )
    }
}

@Composable
private fun ContributionDialog(form: ContributionFormState, currency: String, vm: GoalsViewModel) {
    AlertDialog(
        onDismissRequest = vm::dismissContribution,
        title = { Text(if (form.withdraw) "Tarik dana" else "Tambah setoran") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${form.goal.name} · terkumpul ${MoneyFormatter.format(form.goal.savedAmount, currency)}", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !form.withdraw, onClick = { vm.onWithdraw(false) }, label = { Text("Setoran") })
                    FilterChip(selected = form.withdraw, onClick = { vm.onWithdraw(true) }, label = { Text("Penarikan") })
                }
                OutlinedTextField(
                    value = form.amountInput, onValueChange = vm::onAmount, label = { Text("Nominal") }, prefix = { Text("Rp ") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = form.error != null, supportingText = form.error?.let { { Text(it) } },
                )
                OutlinedTextField(value = form.note, onValueChange = vm::onNote, label = { Text("Catatan (opsional)") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = vm::saveContribution) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = vm::dismissContribution) { Text("Batal") } },
    )
}

@Composable
private fun GoalDetailDialog(goal: SavingGoal, state: GoalsUiState, vm: GoalsViewModel) {
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = vm::closeDetail,
        title = { Text(goal.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Riwayat setoran", style = MaterialTheme.typography.titleMedium)
                if (state.detailHistory.isEmpty()) {
                    Text("Belum ada setoran.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.detailHistory.take(12).forEach { c ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(DateFormats.date(c.contributedAt) + if (c.note.isNotBlank()) " · ${c.note}" else "", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            MoneyFormatter.format(c.amount, state.currencyCode, showPlusSign = true),
                            color = if (c.amount >= 0) DuitKuTheme.extended.income else DuitKuTheme.extended.expense,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { vm.closeDetail(); vm.startContribution(goal) }, label = { Text("Setoran") })
                    AssistChip(onClick = { vm.closeDetail(); vm.startContribution(goal, withdraw = true) }, label = { Text("Tarik") })
                    AssistChip(onClick = { vm.closeDetail(); vm.startEdit(goal) }, label = { Text("Ubah") })
                }
            }
        },
        confirmButton = { TextButton(onClick = vm::closeDetail) { Text("Tutup") } },
        dismissButton = { TextButton(onClick = { confirmDelete = true }) { Text("Hapus", color = MaterialTheme.colorScheme.error) } },
    )
    if (confirmDelete) {
        ConfirmDialog(
            title = "Hapus target?", message = "Target dan seluruh riwayat setorannya akan dihapus.", confirmLabel = "Hapus", destructive = true,
            onConfirm = { confirmDelete = false; vm.delete(goal) }, onDismiss = { confirmDelete = false },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GoalCardPreview() {
    DuitKuTheme(dynamicColor = false) {
        GoalCard(
            SavingGoal(1, "Dana darurat", 30_000_000, 12_000_000, System.currentTimeMillis() + 200L * 86_400_000, "savings", 0xFF0B6B4F.toInt()),
            GoalEstimate(LocalDate.now().plusMonths(8), 2_000_000, true), "IDR", {}, {},
        )
    }
}
