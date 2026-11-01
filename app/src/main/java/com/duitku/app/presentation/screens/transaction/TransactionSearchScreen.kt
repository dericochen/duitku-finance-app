package com.duitku.app.presentation.screens.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import com.duitku.app.core.util.endOfDayMillis
import com.duitku.app.core.util.startOfDayMillis
import com.duitku.app.core.util.toLocalDate
import com.duitku.app.domain.model.CategoryKind
import com.duitku.app.domain.model.TransactionFilter
import com.duitku.app.domain.model.TransactionItem
import com.duitku.app.domain.model.TransactionType
import com.duitku.app.presentation.components.DatePickerDialogLocal
import com.duitku.app.presentation.components.DayHeader
import com.duitku.app.presentation.components.EmptyState
import com.duitku.app.presentation.components.LoadingBox
import com.duitku.app.presentation.components.TransactionRow
import com.duitku.app.presentation.components.groupedByDay
import com.duitku.app.presentation.theme.DuitKuTheme
import java.time.LocalDate

@Composable
fun TransactionSearchScreen(
    onBack: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    viewModel: TransactionSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.queryText.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TransactionSearchEvent.Message -> snackbar.showSnackbar(event.text)
                is TransactionSearchEvent.Deleted -> {
                    snackbar.currentSnackbarData?.dismiss()
                    val result = snackbar.showSnackbar(
                        message = "Transaksi dihapus",
                        actionLabel = "Urungkan",
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undo(event.transaction)
                }
            }
        }
    }

    TransactionSearchContent(
        state = state,
        query = query,
        snackbar = snackbar,
        onBack = onBack,
        onQueryChange = viewModel::onQueryChange,
        onApplyFilter = viewModel::applyFilter,
        onResetFilter = viewModel::resetFilter,
        onOpen = onOpenTransaction,
        onDelete = viewModel::delete,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionSearchContent(
    state: TransactionSearchUiState,
    query: String,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onApplyFilter: (TransactionFilter) -> Unit,
    onResetFilter: () -> Unit,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var showFilter by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                title = {
                    TextField(
                        value = query,
                        onValueChange = onQueryChange,
                        placeholder = { Text("Cari catatan, kategori, dompet, tag, nominal") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = MaterialTheme.colorScheme.surface,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.surface,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { showFilter = true }) {
                        BadgedBox(badge = { if (state.filter.activeCount > 0) Badge { Text("${state.filter.activeCount}") } }) {
                            Icon(Icons.Rounded.FilterList, contentDescription = "Filter")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (!state.isLoading && state.items.isNotEmpty()) {
                ResultSummary(state)
            }
            when {
                state.isLoading -> LoadingBox()
                state.items.isEmpty() -> EmptyState(
                    title = if (state.filter.isEmpty) "Belum ada transaksi" else "Tidak ada hasil",
                    message = if (state.filter.isEmpty) {
                        "Transaksi yang Anda catat akan muncul di sini."
                    } else {
                        "Coba ubah kata kunci atau longgarkan filter."
                    },
                    icon = Icons.Rounded.SearchOff,
                    actionLabel = if (state.filter.activeCount > 0) "Reset filter" else null,
                    onAction = if (state.filter.activeCount > 0) onResetFilter else null,
                )
                else -> TransactionList(state, onOpen, onDelete)
            }
        }
    }

    if (showFilter) {
        FilterSheet(
            state = state,
            onApply = { showFilter = false; onApplyFilter(it) },
            onReset = { showFilter = false; onResetFilter() },
            onDismiss = { showFilter = false },
        )
    }
}

@Composable
private fun ResultSummary(state: TransactionSearchUiState) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("${state.items.size} transaksi", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "+" + MoneyFormatter.compact(state.totalIncome),
                color = DuitKuTheme.extended.income,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                "-" + MoneyFormatter.compact(state.totalExpense),
                color = DuitKuTheme.extended.expense,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionList(
    state: TransactionSearchUiState,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val groups = remember(state.items) { state.items.groupedByDay() }
    LazyColumn(Modifier.fillMaxSize()) {
        groups.forEach { (date, dayItems) ->
            item(key = "h$date") { DayHeader(date) }
            items(dayItems, key = { it.transaction.id }) { item ->
                SwipeToDeleteRow(item = item, currencyCode = state.currencyCode, onOpen = onOpen, onDelete = onDelete)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteRow(
    item: TransactionItem,
    currencyCode: String,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    // Setelah digeser penuh: jalankan hapus lalu kembalikan posisi baris. Bila penghapusan ditolak
    // (bulan tertutup) baris tetap terlihat; bila berhasil, baris hilang lewat aliran data database.
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete(item.transaction.id)
            dismissState.reset()
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        TransactionRow(item = item, currencyCode = currencyCode, onClick = { onOpen(item.transaction.id) })
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    state: TransactionSearchUiState,
    onApply: (TransactionFilter) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = state.filter
    var types by remember { mutableStateOf(initial.types) }
    var walletIds by remember { mutableStateOf(initial.walletIds) }
    var categoryIds by remember { mutableStateOf(initial.categoryIds) }
    var tags by remember { mutableStateOf(initial.tags) }
    var from by remember { mutableStateOf(initial.fromMillis?.toLocalDate()) }
    var to by remember { mutableStateOf(initial.toMillis?.toLocalDate()) }
    var minText by remember { mutableStateOf(initial.minAmount?.toString().orEmpty()) }
    var maxText by remember { mutableStateOf(initial.maxAmount?.toString().orEmpty()) }
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }

    val min = MoneyFormatter.parse(minText)
    val max = MoneyFormatter.parse(maxText)
    val rangeError = if (min != null && max != null && min > max) "Nominal minimum lebih besar dari maksimum" else null
    val dateError = if (from != null && to != null && from!!.isAfter(to)) "Tanggal awal setelah tanggal akhir" else null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Filter transaksi", style = MaterialTheme.typography.titleLarge)

            Text("Jenis", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TransactionType.entries.forEach { type ->
                    FilterChip(
                        selected = type in types,
                        onClick = { types = if (type in types) types - type else types + type },
                        label = { Text(type.label) },
                    )
                }
            }

            Text("Tanggal", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { pickFrom = true }, label = { Text(from?.let(DateFormats::date) ?: "Dari tanggal") })
                AssistChip(onClick = { pickTo = true }, label = { Text(to?.let(DateFormats::date) ?: "Sampai tanggal") })
                if (from != null || to != null) {
                    AssistChip(onClick = { from = null; to = null }, label = { Text("Hapus") })
                }
            }
            dateError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

            Text("Dompet", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.wallets.forEach { wallet ->
                    FilterChip(
                        selected = wallet.id in walletIds,
                        onClick = { walletIds = if (wallet.id in walletIds) walletIds - wallet.id else walletIds + wallet.id },
                        label = { Text(wallet.name) },
                    )
                }
            }

            Text("Kategori", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.categories.filter { it.parentId == null }.forEach { category ->
                    FilterChip(
                        selected = category.id in categoryIds,
                        onClick = { categoryIds = if (category.id in categoryIds) categoryIds - category.id else categoryIds + category.id },
                        label = {
                            Text(if (category.kind == CategoryKind.INCOME) "${category.name} (masuk)" else category.name)
                        },
                    )
                }
            }

            if (state.tags.isNotEmpty()) {
                Text("Tag", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.tags.forEach { tag ->
                        FilterChip(
                            selected = tag in tags,
                            onClick = { tags = if (tag in tags) tags - tag else tags + tag },
                            label = { Text("#$tag") },
                        )
                    }
                }
            }

            Text("Rentang nominal", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = minText,
                    onValueChange = { minText = it.filter(Char::isDigit).take(12) },
                    label = { Text("Minimum") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = maxText,
                    onValueChange = { maxText = it.filter(Char::isDigit).take(12) },
                    label = { Text("Maksimum") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            rangeError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text("Reset") }
                Button(
                    onClick = {
                        onApply(
                            initial.copy(
                                types = types,
                                walletIds = walletIds,
                                categoryIds = categoryIds,
                                tags = tags,
                                fromMillis = from?.startOfDayMillis(),
                                toMillis = to?.endOfDayMillis(),
                                minAmount = min,
                                maxAmount = max,
                            ),
                        )
                    },
                    enabled = rangeError == null && dateError == null,
                    modifier = Modifier.weight(1f),
                ) { Text("Terapkan") }
            }
        }
    }

    if (pickFrom) {
        DatePickerDialogLocal(
            initial = from ?: LocalDate.now(),
            onConfirm = { from = it; pickFrom = false },
            onDismiss = { pickFrom = false },
        )
    }
    if (pickTo) {
        DatePickerDialogLocal(
            initial = to ?: LocalDate.now(),
            onConfirm = { to = it; pickTo = false },
            onDismiss = { pickTo = false },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchEmptyPreview() {
    DuitKuTheme(dynamicColor = false) {
        TransactionSearchContent(
            state = TransactionSearchUiState(isLoading = false, filter = TransactionFilter(query = "kopi")),
            query = "kopi",
            snackbar = remember { SnackbarHostState() },
            onBack = {}, onQueryChange = {}, onApplyFilter = {}, onResetFilter = {}, onOpen = {}, onDelete = {},
        )
    }
}
