package com.duitku.app.presentation.screens.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.duitku.app.presentation.navigation.Screen
import com.duitku.app.presentation.theme.DuitKuTheme

private data class MoreEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String,
)

private val entries = listOf(
    MoreEntry("Dompet", "Tunai, bank, e-wallet, kartu kredit, tabungan", Icons.Rounded.AccountBalanceWallet, Screen.Wallets.route),
    MoreEntry("Transfer antar dompet", "Pindahkan saldo antar dompet", Icons.Rounded.SwapHoriz, Screen.TransactionEdit.create(type = "TRANSFER")),
    MoreEntry("Cari & filter transaksi", "Cari catatan, kategori, tag, nominal", Icons.Rounded.Search, Screen.TransactionSearch.route),
    MoreEntry("Kategori", "Kategori, subkategori, ikon dan warna", Icons.Rounded.Category, Screen.Categories.route),
    MoreEntry("Arsip Mutasi", "Mutasi bulanan tersimpan, tutup buku, ekspor PDF", Icons.Rounded.Inventory2, Screen.Archive.route),
    MoreEntry("Ekspor & Laporan", "PDF, CSV, Excel, laporan tahunan", Icons.Rounded.PictureAsPdf, Screen.ExportCenter.route),
    MoreEntry("Riwayat Ekspor", "Buka kembali PDF yang pernah dibuat", Icons.AutoMirrored.Rounded.ReceiptLong, Screen.ExportHistory.route),
    MoreEntry("Transaksi Berulang", "Gaji, cicilan, langganan", Icons.Rounded.Loop, Screen.Recurring.route),
    MoreEntry("Target Tabungan", "Pantau progres target menabung", Icons.Rounded.Savings, Screen.Goals.route),
    MoreEntry("Utang & Piutang", "Catatan pihak, jatuh tempo, pembayaran", Icons.AutoMirrored.Rounded.ReceiptLong, Screen.Debts.route),
    MoreEntry("Backup & Restore", "File .duitku, backup otomatis", Icons.Rounded.Backup, Screen.Backup.route),
    MoreEntry("Pengaturan", "Tema, keamanan, pengingat, retensi data", Icons.Rounded.Settings, Screen.Settings.route),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Lainnya") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(entries, key = { it.route }) { entry ->
                Column {
                    ListItem(
                        modifier = Modifier.clickable { onNavigate(entry.route) },
                        leadingContent = {
                            Icon(entry.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        headlineContent = { Text(entry.title) },
                        supportingContent = { Text(entry.subtitle) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MoreScreenPreview() {
    DuitKuTheme(dynamicColor = false) { MoreScreen(onNavigate = {}) }
}
