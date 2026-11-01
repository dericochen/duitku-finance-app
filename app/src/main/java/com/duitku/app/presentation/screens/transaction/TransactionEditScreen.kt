package com.duitku.app.presentation.screens.transaction

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duitku.app.core.util.DateFormats
import com.duitku.app.core.util.MoneyFormatter
import com.duitku.app.core.util.toYearMonth
import com.duitku.app.domain.model.Category
import com.duitku.app.domain.model.CategoryKind
import com.duitku.app.domain.model.CategoryNode
import com.duitku.app.domain.model.TransactionType
import com.duitku.app.domain.model.Wallet
import com.duitku.app.domain.model.WalletType
import com.duitku.app.presentation.common.UiEvent
import com.duitku.app.presentation.components.AmountKeypad
import com.duitku.app.presentation.components.ConfirmDialog
import com.duitku.app.presentation.components.DatePickerDialogLocal
import com.duitku.app.presentation.components.DuitKuTopBar
import com.duitku.app.presentation.components.IconBadge
import com.duitku.app.presentation.components.LoadingBox
import com.duitku.app.presentation.components.TimePickerDialogLocal
import com.duitku.app.presentation.components.amountColor
import com.duitku.app.presentation.theme.Dimens
import com.duitku.app.presentation.theme.DuitKuTheme
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@Composable
fun TransactionEditScreen(
    onBack: () -> Unit,
    viewModel: TransactionEditViewModel = hiltViewModel(),
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

    TransactionEditContent(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onTypeChange = viewModel::onTypeChange,
        onKey = viewModel::onKey,
        onBackspace = viewModel::onBackspace,
        onClearAmount = viewModel::onClearAmount,
        onWalletSelected = viewModel::onWalletSelected,
        onToWalletSelected = viewModel::onToWalletSelected,
        onCategorySelected = viewModel::onCategorySelected,
        onSubCategorySelected = viewModel::onSubCategorySelected,
        onNoteChange = viewModel::onNoteChange,
        onTagInputChange = viewModel::onTagInputChange,
        onAddTag = { viewModel.addTag(it) },
        onRemoveTag = viewModel::removeTag,
        onDateChange = viewModel::onDateChange,
        onTimeChange = viewModel::onTimeChange,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onDismissClosedPeriod = viewModel::dismissClosedPeriod,
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditContent(
    state: TransactionEditUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onTypeChange: (TransactionType) -> Unit,
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onClearAmount: () -> Unit,
    onWalletSelected: (Long) -> Unit,
    onToWalletSelected: (Long) -> Unit,
    onCategorySelected: (Long) -> Unit,
    onSubCategorySelected: (Long?) -> Unit,
    onNoteChange: (String) -> Unit,
    onTagInputChange: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onDateChange: (java.time.LocalDate) -> Unit,
    onTimeChange: (java.time.LocalTime) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismissClosedPeriod: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // --- Perbaikan bug keyboard ---------------------------------------------------
    // Keypad nominal kustom hanya tampil saat "mode nominal" aktif. Ketika sebuah
    // kolom teks (Catatan/Tag) mendapat fokus, keyboard sistem muncul dan keypad
    // kustom otomatis disembunyikan sehingga keduanya tidak pernah bertabrakan.
    var amountMode by remember { mutableStateOf(true) }
    val keyboard = LocalSoftwareKeyboardController.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            DuitKuTopBar(
                title = if (state.isEdit) "Ubah Transaksi" else "Transaksi Baru",
                onBack = onBack,
                actions = {
                    if (state.isEdit) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Hapus transaksi")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.isLoading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        Column(Modifier.padding(padding).imePadding()) {
            // ---- Bagian yang bisa digulir ----
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Spacer(Modifier.height(2.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    TransactionType.entries.forEachIndexed { index, type ->
                        SegmentedButton(
                            selected = state.type == type,
                            onClick = { onTypeChange(type) },
                            shape = SegmentedButtonDefaults.itemShape(index, TransactionType.entries.size),
                        ) { Text(type.label) }
                    }
                }

                AmountDisplay(
                    state = state,
                    active = amountMode,
                    onActivate = {
                        amountMode = true
                        keyboard?.hide()
                    },
                )

                WalletSelector(
                    label = if (state.type == TransactionType.TRANSFER) "Dari dompet" else "Dompet",
                    wallets = state.wallets,
                    selectedId = state.walletId,
                    error = state.walletError,
                    onSelected = onWalletSelected,
                    disabledId = null,
                )
                if (state.type == TransactionType.TRANSFER) {
                    WalletSelector(
                        label = "Ke dompet",
                        wallets = state.wallets,
                        selectedId = state.toWalletId,
                        error = state.toWalletError,
                        onSelected = onToWalletSelected,
                        disabledId = state.walletId,
                    )
                } else {
                    CategorySelector(
                        state = state,
                        onOpenSheet = { showCategorySheet = true },
                        onSubCategorySelected = onSubCategorySelected,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { showDatePicker = true },
                        label = { Text(DateFormats.date(state.dateTime.toLocalDate())) },
                        leadingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            leadingIconContentColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                    AssistChip(
                        onClick = { showTimePicker = true },
                        label = { Text(DateFormats.time(state.dateTime.toLocalTime())) },
                        leadingIcon = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            leadingIconContentColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
                state.dateError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

                NeoField(
                    value = state.note,
                    onValueChange = onNoteChange,
                    label = "Catatan (opsional)",
                    isError = state.noteError != null,
                    supportingText = state.noteError ?: "${state.note.length}/200",
                    maxLines = 3,
                    onFocused = { amountMode = false },
                )

                TagEditor(state, onTagInputChange, onAddTag, onRemoveTag, onFocused = { amountMode = false })
                Spacer(Modifier.height(4.dp))
            }

            // ---- Keypad nominal: hanya saat mode nominal, tidak pernah bareng IME sistem ----
            AnimatedVisibility(
                visible = amountMode,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                AmountKeypad(onKey = onKey, onBackspace = onBackspace, onClear = onClearAmount)
            }
            Button(
                onClick = onSave,
                enabled = !state.isSaving,
                shape = Dimens.buttonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DuitKuTheme.extended.accent,
                    contentColor = DuitKuTheme.extended.onAccent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .padding(start = 16.dp, end = 16.dp)
                    .navigationBarsPadding(),
            ) {
                Text(
                    if (state.isSaving) "Menyimpan..." else "Simpan",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showDatePicker) {
        DatePickerDialogLocal(
            initial = state.dateTime.toLocalDate(),
            onConfirm = { showDatePicker = false; onDateChange(it) },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showTimePicker) {
        TimePickerDialogLocal(
            initial = state.dateTime.toLocalTime(),
            onConfirm = { showTimePicker = false; onTimeChange(it) },
            onDismiss = { showTimePicker = false },
        )
    }
    if (showCategorySheet) {
        CategorySheet(
            tree = state.categoryTree,
            selectedId = state.categoryId,
            onSelected = { showCategorySheet = false; onCategorySelected(it) },
            onDismiss = { showCategorySheet = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Hapus transaksi?",
            message = "Transaksi ini akan dihapus dari catatan Anda.",
            confirmLabel = "Hapus",
            destructive = true,
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
    state.closedPeriodYm?.let { ym ->
        ConfirmDialog(
            title = "Bulan sudah ditutup",
            message = "Mutasi ${DateFormats.monthYear(ym.toYearMonth())} sudah ditutup (read-only). " +
                "Buka kembali bulan tersebut dari menu Arsip Mutasi bila perlu mengubahnya.",
            confirmLabel = "Mengerti",
            dismissLabel = "Tutup",
            onConfirm = onDismissClosedPeriod,
            onDismiss = onDismissClosedPeriod,
        )
    }
}

/** Panel nominal besar. Mengetuk panel mengaktifkan keypad kustom & menutup keyboard sistem. */
@Composable
private fun AmountDisplay(
    state: TransactionEditUiState,
    active: Boolean,
    onActivate: () -> Unit,
) {
    val color = amountColor(state.type)
    val shape = Dimens.balanceCardShape
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = if (active) color.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline,
                shape = shape,
            )
            .clickable(onClick = onActivate)
            .padding(vertical = 20.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Nominal",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = MoneyFormatter.format(state.amount, state.currencyCode),
            style = MaterialTheme.typography.displaySmall,
            color = if (state.amountInput.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else color,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = state.amountError
                ?: if (active) "Ketik nominal dengan keypad di bawah" else "Ketuk untuk mengisi nominal",
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.amountError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Kolom teks bergaya Neo Finance dengan radius lembut; melapor saat mendapat fokus. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NeoField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
    singleLine: Boolean = false,
    maxLines: Int = 1,
    imeAction: ImeAction = ImeAction.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    placeholder: String? = null,
    onFocused: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = singleLine,
        maxLines = maxLines,
        shape = Dimens.inputShape,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = keyboardActions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onFocused() },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WalletSelector(
    label: String,
    wallets: List<Wallet>,
    selectedId: Long?,
    error: String?,
    onSelected: (Long) -> Unit,
    disabledId: Long?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (wallets.isEmpty()) {
            Text("Belum ada dompet aktif. Buat dompet dari menu Lainnya.", color = MaterialTheme.colorScheme.error)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            wallets.forEach { wallet ->
                FilterChip(
                    selected = wallet.id == selectedId,
                    onClick = { onSelected(wallet.id) },
                    enabled = wallet.id != disabledId,
                    label = { Text(wallet.name) },
                    leadingIcon = { IconBadge(wallet.iconKey, wallet.colorArgb, size = 22.dp) },
                )
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySelector(
    state: TransactionEditUiState,
    onOpenSheet: () -> Unit,
    onSubCategorySelected: (Long?) -> Unit,
) {
    val selected = state.selectedCategory
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Kategori", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(Dimens.inputShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, if (state.categoryError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline, Dimens.inputShape)
                .clickable(onClick = onOpenSheet)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selected != null) {
                IconBadge(selected.category.iconKey, selected.category.colorArgb, size = 36.dp)
                Text(selected.category.name, style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(
                    text = if (state.categoryTree.isEmpty()) "Belum ada kategori" else "Pilih kategori",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.categoryError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        if (selected != null && selected.children.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                selected.children.forEach { child ->
                    FilterChip(
                        selected = state.subCategoryId == child.id,
                        onClick = { onSubCategorySelected(if (state.subCategoryId == child.id) null else child.id) },
                        label = { Text(child.name) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CategorySheet(
    tree: List<CategoryNode>,
    selectedId: Long?,
    onSelected: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Pilih kategori", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
            if (tree.isEmpty()) {
                Text("Belum ada kategori. Tambahkan dari menu Lainnya > Kategori.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tree.forEach { node ->
                    FilterChip(
                        selected = node.category.id == selectedId,
                        onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { onSelected(node.category.id) } },
                        label = { Text(node.category.name) },
                        leadingIcon = { IconBadge(node.category.iconKey, node.category.colorArgb, size = 24.dp) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TagEditor(
    state: TransactionEditUiState,
    onTagInputChange: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onFocused: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tag", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { onRemoveTag(tag) },
                        label = { Text("#$tag") },
                        trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = "Hapus tag $tag") },
                    )
                }
            }
        }
        NeoField(
            value = state.tagInput,
            onValueChange = onTagInputChange,
            label = "Tag",
            placeholder = "Ketik tag lalu tekan selesai",
            singleLine = true,
            isError = state.tagError != null,
            supportingText = state.tagError,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { onAddTag(state.tagInput) }),
            onFocused = onFocused,
        )
        val suggestions = state.tagSuggestions.filter { it !in state.tags }.take(6)
        if (suggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { tag ->
                    SuggestionChip(onClick = { onAddTag(tag) }, label = { Text("#$tag") })
                }
            }
        }
    }
}

private val previewWallets = listOf(
    Wallet(1, "Tunai", WalletType.CASH, 0, "cash", 0xFF0B6B4F.toInt()),
    Wallet(2, "BCA", WalletType.BANK, 0, "bank", 0xFF0277BD.toInt()),
)

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun TransactionEditPreview() {
    DuitKuTheme(dynamicColor = false) {
        TransactionEditContent(
            state = TransactionEditUiState(
                isLoading = false,
                amountInput = "45000",
                walletId = 1,
                wallets = previewWallets,
                categoryTree = listOf(
                    CategoryNode(
                        Category(1, "Makanan", CategoryKind.EXPENSE, null, "food", 0xFFE65100.toInt()),
                        listOf(Category(2, "Kopi & jajan", CategoryKind.EXPENSE, 1, "coffee", 0xFFE65100.toInt())),
                    ),
                ),
                categoryId = 1,
                dateTime = LocalDateTime.of(2026, 11, 1, 12, 30),
                tags = listOf("kantor"),
            ),
            snackbar = remember { SnackbarHostState() },
            onBack = {}, onTypeChange = {}, onKey = {}, onBackspace = {}, onClearAmount = {},
            onWalletSelected = {}, onToWalletSelected = {}, onCategorySelected = {}, onSubCategorySelected = {},
            onNoteChange = {}, onTagInputChange = {}, onAddTag = {}, onRemoveTag = {}, onDateChange = {},
            onTimeChange = {}, onSave = {}, onDelete = {}, onDismissClosedPeriod = {},
        )
    }
}
