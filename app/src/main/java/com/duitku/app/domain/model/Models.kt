package com.duitku.app.domain.model

data class Wallet(
    val id: Long = 0,
    val name: String,
    val type: WalletType,
    val initialBalance: Long,
    val iconKey: String,
    val colorArgb: Int,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val openedAt: Long = 0,
)

data class WalletWithBalance(
    val wallet: Wallet,
    val balance: Long,
)

data class Category(
    val id: Long = 0,
    val name: String,
    val kind: CategoryKind,
    val parentId: Long? = null,
    val iconKey: String,
    val colorArgb: Int,
    val isDefault: Boolean = false,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
) {
    val isSub: Boolean get() = parentId != null
}

/** Kategori utama beserta subkategorinya. */
data class CategoryNode(
    val category: Category,
    val children: List<Category>,
)

data class Transaction(
    val id: Long = 0,
    val type: TransactionType,
    val amount: Long,
    val walletId: Long,
    val toWalletId: Long? = null,
    val categoryId: Long? = null,
    val subCategoryId: Long? = null,
    val note: String = "",
    val tags: List<String> = emptyList(),
    val occurredAt: Long,
    val recurringId: Long? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    /** Efek transaksi terhadap saldo [walletId]: positif menambah, negatif mengurangi, 0 bila tidak terkait. */
    fun effectOn(targetWalletId: Long): Long = when (type) {
        TransactionType.INCOME -> if (walletId == targetWalletId) amount else 0
        TransactionType.EXPENSE -> if (walletId == targetWalletId) -amount else 0
        TransactionType.TRANSFER -> when (targetWalletId) {
            walletId -> -amount
            toWalletId -> amount
            else -> 0
        }
    }

    val isRecurring: Boolean get() = recurringId != null
}

/** Transaksi yang sudah dilengkapi nama/ikon/warna dompet & kategori untuk ditampilkan di UI. */
data class TransactionItem(
    val transaction: Transaction,
    val walletName: String,
    val walletColorArgb: Int,
    val toWalletName: String?,
    val categoryName: String?,
    val subCategoryName: String?,
    val iconKey: String,
    val colorArgb: Int,
)

/** Kriteria pencarian & filter transaksi. Semua kriteria bersifat AND. */
data class TransactionFilter(
    val query: String = "",
    val fromMillis: Long? = null,
    val toMillis: Long? = null,
    val types: Set<TransactionType> = emptySet(),
    val walletIds: Set<Long> = emptySet(),
    val categoryIds: Set<Long> = emptySet(),
    val tags: Set<String> = emptySet(),
    val minAmount: Long? = null,
    val maxAmount: Long? = null,
) {
    val isEmpty: Boolean
        get() = query.isBlank() && fromMillis == null && toMillis == null && types.isEmpty() &&
            walletIds.isEmpty() && categoryIds.isEmpty() && tags.isEmpty() && minAmount == null && maxAmount == null

    /** Jumlah filter aktif (di luar kata kunci), untuk badge pada tombol filter. */
    val activeCount: Int
        get() = listOf(
            fromMillis != null || toMillis != null,
            types.isNotEmpty(),
            walletIds.isNotEmpty(),
            categoryIds.isNotEmpty(),
            tags.isNotEmpty(),
            minAmount != null || maxAmount != null,
        ).count { it }
}
