package com.duitku.app.presentation.screens.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duitku.app.domain.model.WalletType
import com.duitku.app.presentation.common.UiEvent
import com.duitku.app.presentation.components.ColorPicker
import com.duitku.app.presentation.components.ConfirmDialog
import com.duitku.app.presentation.components.DuitKuTopBar
import com.duitku.app.presentation.components.IconCatalog
import com.duitku.app.presentation.components.IconPicker
import com.duitku.app.presentation.components.LoadingBox
import com.duitku.app.presentation.theme.DuitKuTheme

@Composable
fun WalletEditScreen(
    onBack: () -> Unit,
    viewModel: WalletEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.ShowMessage -> snackbar.showSnackbar(event.text)
                UiEvent.NavigateBack -> onBack()
            }
        }
    }

    WalletEditContent(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onNameChange = viewModel::onNameChange,
        onTypeChange = viewModel::onTypeChange,
        onBalanceChange = viewModel::onBalanceChange,
        onNegativeChange = viewModel::onNegativeChange,
        onIconChange = viewModel::onIconChange,
        onColorChange = viewModel::onColorChange,
        onSave = viewModel::save,
        onToggleArchive = viewModel::toggleArchive,
        onDelete = viewModel::delete,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WalletEditContent(
    state: WalletEditUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onTypeChange: (WalletType) -> Unit,
    onBalanceChange: (String) -> Unit,
    onNegativeChange: (Boolean) -> Unit,
    onIconChange: (String) -> Unit,
    onColorChange: (Int) -> Unit,
    onSave: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { DuitKuTopBar(title = if (state.isEdit) "Ubah Dompet" else "Dompet Baru", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.isLoading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text("Nama dompet") },
                singleLine = true,
                isError = state.nameError != null,
                supportingText = state.nameError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Jenis", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WalletType.entries.forEach { type ->
                    FilterChip(
                        selected = state.type == type,
                        onClick = { onTypeChange(type) },
                        label = { Text(type.label) },
                    )
                }
            }

            OutlinedTextField(
                value = state.balanceInput,
                onValueChange = onBalanceChange,
                label = { Text("Saldo awal") },
                prefix = { Text(if (state.balanceNegative) "-Rp " else "Rp ") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = state.balanceError != null,
                supportingText = {
                    Text(state.balanceError ?: "Saldo saat Anda mulai mencatat. Kosongkan bila 0.")
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.type == WalletType.CREDIT_CARD) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = state.balanceNegative, onCheckedChange = onNegativeChange)
                    Text("Saldo awal berupa tagihan/utang kartu", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Text("Warna", style = MaterialTheme.typography.labelLarge)
            ColorPicker(selectedArgb = state.colorArgb, onSelected = onColorChange)

            Text("Ikon", style = MaterialTheme.typography.labelLarge)
            IconPicker(
                selectedKey = state.iconKey,
                colorArgb = state.colorArgb,
                onSelected = onIconChange,
                keys = IconCatalog.walletKeys,
            )

            Button(onClick = onSave, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.isSaving) "Menyimpan..." else "Simpan")
            }

            if (state.isEdit) {
                OutlinedButton(onClick = onToggleArchive, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.isArchived) "Aktifkan kembali" else "Arsipkan dompet")
                }
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Hapus dompet", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Hapus dompet?",
            message = "Dompet tanpa transaksi akan dihapus permanen. Dompet yang sudah punya transaksi hanya diarsipkan " +
                "supaya riwayat dan saldo Anda tidak hilang.",
            confirmLabel = "Hapus",
            destructive = true,
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WalletEditPreview() {
    DuitKuTheme(dynamicColor = false) {
        WalletEditContent(
            state = WalletEditUiState(isLoading = false, isEdit = true, name = "Bank BCA", type = WalletType.BANK, iconKey = "bank"),
            snackbar = remember { SnackbarHostState() },
            onBack = {}, onNameChange = {}, onTypeChange = {}, onBalanceChange = {}, onNegativeChange = {},
            onIconChange = {}, onColorChange = {}, onSave = {}, onToggleArchive = {}, onDelete = {},
        )
    }
}
