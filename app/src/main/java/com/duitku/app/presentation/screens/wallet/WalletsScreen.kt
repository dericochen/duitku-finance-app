package com.duitku.app.presentation.screens.wallet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duitku.app.core.util.MoneyFormatter
import com.duitku.app.domain.model.Wallet
import com.duitku.app.domain.model.WalletType
import com.duitku.app.domain.model.WalletWithBalance
import com.duitku.app.presentation.components.DuitKuTopBar
import com.duitku.app.presentation.components.EmptyState
import com.duitku.app.presentation.components.IconBadge
import com.duitku.app.presentation.components.LoadingBox
import com.duitku.app.presentation.theme.DuitKuTheme

@Composable
fun WalletsScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: WalletsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WalletsContent(state = state, onBack = onBack, onAdd = onAdd, onEdit = onEdit)
}

@Composable
fun WalletsContent(
    state: WalletsUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    Scaffold(
        topBar = { DuitKuTopBar(title = "Dompet", onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Tambah dompet") },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingBox(Modifier.padding(padding))
            state.active.isEmpty() && state.archived.isEmpty() -> EmptyState(
                title = "Belum ada dompet",
                message = "Tambahkan dompet pertama Anda: tunai, rekening bank, e-wallet, atau kartu kredit.",
                modifier = Modifier.padding(padding),
                actionLabel = "Tambah dompet",
                onAction = onAdd,
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "total") { TotalCard(state.totalBalance, state.currencyCode) }
                items(state.active, key = { it.wallet.id }) { item ->
                    WalletCard(item, state.currencyCode, onClick = { onEdit(item.wallet.id) })
                }
                if (state.archived.isNotEmpty()) {
                    item(key = "archived-title") {
                        Text(
                            "Diarsipkan",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    items(state.archived, key = { "a${it.wallet.id}" }) { item ->
                        WalletCard(item, state.currencyCode, onClick = { onEdit(item.wallet.id) }, dimmed = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalCard(total: Long, currencyCode: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Total saldo semua dompet", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                MoneyFormatter.format(total, currencyCode),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun WalletCard(item: WalletWithBalance, currencyCode: String, onClick: () -> Unit, dimmed: Boolean = false) {
    val wallet = item.wallet
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconBadge(iconKey = wallet.iconKey, colorArgb = wallet.colorArgb, size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text(wallet.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    wallet.type.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                MoneyFormatter.format(item.balance, currencyCode),
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    dimmed -> MaterialTheme.colorScheme.onSurfaceVariant
                    item.balance < 0 -> DuitKuTheme.extended.expense
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WalletsContentPreview() {
    DuitKuTheme(dynamicColor = false) {
        WalletsContent(
            state = WalletsUiState(
                isLoading = false,
                active = listOf(
                    WalletWithBalance(Wallet(1, "Tunai", WalletType.CASH, 0, "cash", 0xFF0B6B4F.toInt()), 450_000),
                    WalletWithBalance(Wallet(2, "BCA", WalletType.BANK, 0, "bank", 0xFF0277BD.toInt()), 12_300_000),
                    WalletWithBalance(Wallet(3, "Kartu Kredit", WalletType.CREDIT_CARD, 0, "card", 0xFFC62828.toInt()), -850_000),
                ),
                totalBalance = 11_900_000,
            ),
            onBack = {}, onAdd = {}, onEdit = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WalletsEmptyPreview() {
    DuitKuTheme(dynamicColor = false) {
        WalletsContent(state = WalletsUiState(isLoading = false), onBack = {}, onAdd = {}, onEdit = {})
    }
}
