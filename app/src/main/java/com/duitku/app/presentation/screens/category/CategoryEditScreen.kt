package com.duitku.app.presentation.screens.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duitku.app.presentation.common.UiEvent
import com.duitku.app.presentation.components.ColorPicker
import com.duitku.app.presentation.components.ConfirmDialog
import com.duitku.app.presentation.components.DuitKuTopBar
import com.duitku.app.presentation.components.IconBadge
import com.duitku.app.presentation.components.IconPicker
import com.duitku.app.presentation.components.LoadingBox
import com.duitku.app.presentation.theme.DuitKuTheme

@Composable
fun CategoryEditScreen(
    onBack: () -> Unit,
    viewModel: CategoryEditViewModel = hiltViewModel(),
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

    CategoryEditContent(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onNameChange = viewModel::onNameChange,
        onIconChange = viewModel::onIconChange,
        onColorChange = viewModel::onColorChange,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
    )
}

@Composable
fun CategoryEditContent(
    state: CategoryEditUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onIconChange: (String) -> Unit,
    onColorChange: (Int) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val title = when {
        state.isEdit && state.isSub -> "Ubah Subkategori"
        state.isEdit -> "Ubah Kategori"
        state.isSub -> "Subkategori Baru"
        else -> "Kategori Baru"
    }

    Scaffold(
        topBar = { DuitKuTopBar(title = title, onBack = onBack) },
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(iconKey = state.iconKey, colorArgb = state.colorArgb, size = 56.dp)
                Column {
                    Text(state.kind.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    state.parentName?.let {
                        Text("Di dalam: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text("Nama") },
                singleLine = true,
                isError = state.nameError != null,
                supportingText = state.nameError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Warna", style = MaterialTheme.typography.labelLarge)
            ColorPicker(selectedArgb = state.colorArgb, onSelected = onColorChange)
            Text("Ikon", style = MaterialTheme.typography.labelLarge)
            IconPicker(selectedKey = state.iconKey, colorArgb = state.colorArgb, onSelected = onIconChange)

            Button(onClick = onSave, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.isSaving) "Menyimpan..." else "Simpan")
            }
            if (state.isEdit) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Hapus kategori", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Hapus kategori?",
            message = "Kategori yang sudah dipakai transaksi atau anggaran hanya diarsipkan agar riwayat tidak hilang.",
            confirmLabel = "Hapus",
            destructive = true,
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CategoryEditPreview() {
    DuitKuTheme(dynamicColor = false) {
        CategoryEditContent(
            state = CategoryEditUiState(isLoading = false, isEdit = true, name = "Kopi & jajan", parentName = "Makanan", iconKey = "coffee"),
            snackbar = remember { SnackbarHostState() },
            onBack = {}, onNameChange = {}, onIconChange = {}, onColorChange = {}, onSave = {}, onDelete = {},
        )
    }
}
