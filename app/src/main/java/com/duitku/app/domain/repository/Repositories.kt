package com.duitku.app.domain.repository

import com.duitku.app.domain.model.Category
import com.duitku.app.domain.model.CategoryKind
import com.duitku.app.domain.model.CategoryNode
import com.duitku.app.domain.model.Transaction
import com.duitku.app.domain.model.TransactionFilter
import com.duitku.app.domain.model.Wallet
import com.duitku.app.domain.model.WalletWithBalance
import kotlinx.coroutines.flow.Flow

/** Dilempar saat mengubah data pada periode yang sudah ditutup (read-only). */
class PeriodClosedException(val periodYm: Int) :
    IllegalStateException("Periode $periodYm sudah ditutup")

interface WalletRepository {
    fun observeWallets(): Flow<List<Wallet>>
    fun observeWalletsWithBalance(): Flow<List<WalletWithBalance>>
    suspend fun getWallet(id: Long): Wallet?
    suspend fun count(): Int

    /** Menyimpan (insert bila id == 0, update bila tidak). Mengembalikan id dompet. */
    suspend fun save(wallet: Wallet): Long
    suspend fun setArchived(id: Long, archived: Boolean)

    /** Jumlah transaksi yang terkait dompet (sebagai asal maupun tujuan). */
    suspend fun transactionCount(id: Long): Int
    suspend fun delete(id: Long)
}

interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>
    fun observeTree(kind: CategoryKind): Flow<List<CategoryNode>>
    suspend fun get(id: Long): Category?
    suspend fun count(): Int
    suspend fun save(category: Category): Long
    suspend fun setArchived(id: Long, archived: Boolean)
    suspend fun usageCount(id: Long): Int
    suspend fun hasChildren(id: Long): Boolean
    suspend fun delete(id: Long)
    suspend fun insertAll(categories: List<Category>): List<Long>
}

interface TransactionRepository {
    fun observeFiltered(filter: TransactionFilter): Flow<List<Transaction>>
    fun observeRecent(limit: Int): Flow<List<Transaction>>
    fun observeById(id: Long): Flow<Transaction?>
    suspend fun get(id: Long): Transaction?

    /**
     * Menyimpan transaksi. Dilempar [PeriodClosedException] bila tanggal lama/baru berada di periode tertutup.
     * Perubahan pada periode yang sudah diarsipkan dicatat ke audit log.
     */
    suspend fun save(transaction: Transaction): Long

    /** Menghapus transaksi dan mengembalikannya (untuk fitur Urungkan). */
    suspend fun delete(id: Long): Transaction?

    /** Memasukkan kembali transaksi yang baru dihapus dengan id aslinya. */
    suspend fun restore(transaction: Transaction)

    suspend fun allTags(): List<String>

    /** Waktu transaksi tertua (null bila belum ada transaksi). Untuk indikator penyimpanan data. */
    fun observeOldestAt(): Flow<Long?>
    fun observeCount(): Flow<Int>
}

/** Membuat data contoh (opsional) untuk demo grafik dan ekspor. Diimplementasikan di layer data. */
interface DemoDataLoader {
    /** @return jumlah transaksi contoh yang dibuat. */
    suspend fun load(): Int
}
