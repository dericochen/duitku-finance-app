package com.duitku.app.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.duitku.app.core.util.DateFormats
import com.duitku.app.core.util.MoneyFormatter
import com.duitku.app.core.util.toLocalDate
import com.duitku.app.domain.model.Transaction
import com.duitku.app.domain.model.TransactionItem
import com.duitku.app.domain.model.TransactionType
import com.duitku.app.presentation.theme.DuitKuTheme
import java.time.LocalDate

@Composable
fun amountColor(type: TransactionType) = when (type) {
    TransactionType.INCOME -> DuitKuTheme.extended.income
    TransactionType.EXPENSE -> DuitKuTheme.extended.expense
    TransactionType.TRANSFER -> DuitKuTheme.extended.transfer
}

/** Teks nominal bertanda: +Rp pemasukan (hijau), -Rp pengeluaran (merah), transfer biru tanpa tanda. */
fun signedAmountText(type: TransactionType, amount: Long, currencyCode: String): String = when (type) {
    TransactionType.INCOME -> "+" + MoneyFormatter.format(amount, currencyCode)
    TransactionType.EXPENSE -> "-" + MoneyFormatter.format(amount, currencyCode)
    TransactionType.TRANSFER -> MoneyFormatter.format(amount, currencyCode)
}

@Composable
fun TransactionRow(
    item: TransactionItem,
    currencyCode: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showWallet: Boolean = true,
) {
    val tx = item.transaction
    // Surface (bukan transparan) agar tidak tembus saat digeser di SwipeToDismiss.
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBadge(iconKey = item.iconKey, colorArgb = item.colorArgb)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = titleOf(item),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (tx.isRecurring) {
                        Icon(
                            Icons.Rounded.Loop,
                            contentDescription = "Transaksi berulang",
                            modifier = Modifier.padding(start = 6.dp),
                            tint = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
                Text(
                    text = subtitleOf(item, showWallet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = signedAmountText(tx.type, tx.amount, currencyCode),
                style = MaterialTheme.typography.titleMedium,
                color = amountColor(tx.type),
                maxLines = 1,
            )
        }
    }
}

private fun titleOf(item: TransactionItem): String = when (item.transaction.type) {
    TransactionType.TRANSFER -> "${item.walletName} → ${item.toWalletName ?: "?"}"
    else -> item.subCategoryName?.let { "${item.categoryName} › $it" } ?: item.categoryName.orEmpty()
}

private fun subtitleOf(item: TransactionItem, showWallet: Boolean): String {
    val parts = mutableListOf(DateFormats.time(item.transaction.occurredAt))
    if (showWallet && item.transaction.type != TransactionType.TRANSFER) parts += item.walletName
    if (item.transaction.note.isNotBlank()) parts += item.transaction.note
    return parts.joinToString(" · ")
}

@Composable
fun DayHeader(date: LocalDate, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = DateFormats.dayHeader(date),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Mengelompokkan transaksi menurut tanggal lokal, urutan sesuai input. */
fun List<TransactionItem>.groupedByDay(): List<Pair<LocalDate, List<TransactionItem>>> =
    groupBy { it.transaction.occurredAt.toLocalDate() }.toList()

@Preview(showBackground = true)
@Composable
private fun TransactionRowPreview() {
    DuitKuTheme(dynamicColor = false) {
        Column {
            TransactionRow(
                item = TransactionItem(
                    transaction = Transaction(
                        id = 1, type = TransactionType.EXPENSE, amount = 45_000, walletId = 1,
                        categoryId = 1, note = "Makan siang", occurredAt = System.currentTimeMillis(),
                    ),
                    walletName = "Tunai", walletColorArgb = 0xFF0B6B4F.toInt(), toWalletName = null,
                    categoryName = "Makanan", subCategoryName = "Makan di luar",
                    iconKey = "food", colorArgb = 0xFFE65100.toInt(),
                ),
                currencyCode = "IDR",
                onClick = {},
            )
            TransactionRow(
                item = TransactionItem(
                    transaction = Transaction(
                        id = 2, type = TransactionType.INCOME, amount = 8_500_000, walletId = 1,
                        categoryId = 2, occurredAt = System.currentTimeMillis(), recurringId = 3,
                    ),
                    walletName = "Bank", walletColorArgb = 0xFF0B6B4F.toInt(), toWalletName = null,
                    categoryName = "Gaji", subCategoryName = null,
                    iconKey = "salary", colorArgb = 0xFF0B6B4F.toInt(),
                ),
                currencyCode = "IDR",
                onClick = {},
            )
        }
    }
}
