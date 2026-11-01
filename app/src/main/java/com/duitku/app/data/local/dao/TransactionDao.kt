package com.duitku.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.duitku.app.data.local.entity.TransactionEntity
import com.duitku.app.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

/** Total per kategori (categoryId null = tanpa kategori). */
data class CategoryTotalRow(val categoryId: Long?, val total: Long, val txCount: Int)

/** Total per hari & tipe untuk grafik; [day] berformat yyyy-MM-dd (waktu lokal). */
data class DayTotalRow(val day: String, val type: TransactionType, val total: Long)

data class WalletBalanceRow(val walletId: Long, val balance: Long)

data class TypeTotalRow(val type: TransactionType, val total: Long, val txCount: Int)

@Dao
interface TransactionDao {

    // ---------- CRUD ----------
    @Insert
    suspend fun insert(entity: TransactionEntity): Long

    @Insert
    suspend fun insertAll(entities: List<TransactionEntity>)

    @Update
    suspend fun update(entity: TransactionEntity)

    @Delete
    suspend fun delete(entity: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: Long): Flow<TransactionEntity?>

    // ---------- Daftar ----------
    @Query("SELECT * FROM transactions WHERE occurredAt BETWEEN :start AND :end ORDER BY occurredAt DESC, id DESC")
    fun observeBetween(start: Long, end: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE occurredAt BETWEEN :start AND :end ORDER BY occurredAt ASC, id ASC")
    suspend fun getBetweenAscending(start: Long, end: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY occurredAt ASC, id ASC")
    suspend fun getAllAscending(): List<TransactionEntity>

    /**
     * Pencarian/filter dinamis. Query dibangun oleh [com.duitku.app.data.repository.TransactionFilterQuery]
     * dengan parameter terikat (bukan penyambungan string) sehingga aman dari SQL injection.
     */
    @RawQuery(observedEntities = [TransactionEntity::class])
    fun observeFiltered(query: SupportSQLiteQuery): Flow<List<TransactionEntity>>

    @RawQuery
    suspend fun getFiltered(query: SupportSQLiteQuery): List<TransactionEntity>

    // ---------- Agregat ----------
    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = :type AND occurredAt BETWEEN :start AND :end")
    fun observeTotal(type: TransactionType, start: Long, end: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = :type AND occurredAt BETWEEN :start AND :end")
    suspend fun getTotal(type: TransactionType, start: Long, end: Long): Long

    @Query(
        """SELECT type AS type, COALESCE(SUM(amount), 0) AS total, COUNT(*) AS txCount
           FROM transactions WHERE occurredAt BETWEEN :start AND :end GROUP BY type""",
    )
    fun observeTypeTotals(start: Long, end: Long): Flow<List<TypeTotalRow>>

    @Query(
        """SELECT type AS type, COALESCE(SUM(amount), 0) AS total, COUNT(*) AS txCount
           FROM transactions WHERE occurredAt BETWEEN :start AND :end GROUP BY type""",
    )
    suspend fun getTypeTotals(start: Long, end: Long): List<TypeTotalRow>

    @Query(
        """SELECT categoryId AS categoryId, COALESCE(SUM(amount), 0) AS total, COUNT(*) AS txCount
           FROM transactions
           WHERE type = :type AND occurredAt BETWEEN :start AND :end
           GROUP BY categoryId ORDER BY total DESC""",
    )
    fun observeCategoryTotals(type: TransactionType, start: Long, end: Long): Flow<List<CategoryTotalRow>>

    @Query(
        """SELECT categoryId AS categoryId, COALESCE(SUM(amount), 0) AS total, COUNT(*) AS txCount
           FROM transactions
           WHERE type = :type AND occurredAt BETWEEN :start AND :end
           GROUP BY categoryId ORDER BY total DESC""",
    )
    suspend fun getCategoryTotals(type: TransactionType, start: Long, end: Long): List<CategoryTotalRow>

    /** Total pengeluaran satu kategori (termasuk semua subkategorinya karena categoryId = induk). */
    @Query(
        """SELECT COALESCE(SUM(amount), 0) FROM transactions
           WHERE type = 'EXPENSE' AND categoryId = :categoryId AND occurredAt BETWEEN :start AND :end""",
    )
    suspend fun getExpenseForCategory(categoryId: Long, start: Long, end: Long): Long

    /** Pemasukan/pengeluaran per hari (offset zona waktu dalam detik dihitung oleh pemanggil). */
    @Query(
        """SELECT strftime('%Y-%m-%d', occurredAt / 1000 + :offsetSeconds, 'unixepoch') AS day,
                  type AS type, COALESCE(SUM(amount), 0) AS total
           FROM transactions
           WHERE occurredAt BETWEEN :start AND :end AND type IN ('INCOME', 'EXPENSE')
           GROUP BY day, type ORDER BY day ASC""",
    )
    fun observeDayTotals(start: Long, end: Long, offsetSeconds: Int): Flow<List<DayTotalRow>>

    @Query(
        """SELECT strftime('%Y-%m-%d', occurredAt / 1000 + :offsetSeconds, 'unixepoch') AS day,
                  type AS type, COALESCE(SUM(amount), 0) AS total
           FROM transactions
           WHERE occurredAt BETWEEN :start AND :end AND type IN ('INCOME', 'EXPENSE')
           GROUP BY day, type ORDER BY day ASC""",
    )
    suspend fun getDayTotals(start: Long, end: Long, offsetSeconds: Int): List<DayTotalRow>

    // ---------- Saldo ----------
    /** Saldo semua dompet = saldo awal + pemasukan - pengeluaran - transfer keluar + transfer masuk. */
    @Query(
        """SELECT w.id AS walletId,
             w.initialBalance
             + COALESCE((SELECT SUM(CASE t.type WHEN 'INCOME' THEN t.amount ELSE -t.amount END)
                         FROM transactions t WHERE t.walletId = w.id), 0)
             + COALESCE((SELECT SUM(t.amount) FROM transactions t
                         WHERE t.toWalletId = w.id AND t.type = 'TRANSFER'), 0) AS balance
           FROM wallets w""",
    )
    fun observeWalletBalances(): Flow<List<WalletBalanceRow>>

    @Query(
        """SELECT w.id AS walletId,
             w.initialBalance
             + COALESCE((SELECT SUM(CASE t.type WHEN 'INCOME' THEN t.amount ELSE -t.amount END)
                         FROM transactions t WHERE t.walletId = w.id), 0)
             + COALESCE((SELECT SUM(t.amount) FROM transactions t
                         WHERE t.toWalletId = w.id AND t.type = 'TRANSFER'), 0) AS balance
           FROM wallets w""",
    )
    suspend fun getWalletBalances(): List<WalletBalanceRow>

    /**
     * Saldo tiap dompet pada saat [before] (transaksi dengan waktu < before; saldo awal dihitung bila openedAt < before).
     * Saldo awal periode = hasil query ini pada awal periode, sehingga saldo akhir bulan lalu = saldo awal bulan ini.
     */
    @Query(
        """SELECT w.id AS walletId,
             (CASE WHEN w.openedAt < :before THEN w.initialBalance ELSE 0 END)
             + COALESCE((SELECT SUM(CASE t.type WHEN 'INCOME' THEN t.amount ELSE -t.amount END)
                         FROM transactions t WHERE t.walletId = w.id AND t.occurredAt < :before), 0)
             + COALESCE((SELECT SUM(t.amount) FROM transactions t
                         WHERE t.toWalletId = w.id AND t.type = 'TRANSFER' AND t.occurredAt < :before), 0) AS balance
           FROM wallets w""",
    )
    suspend fun getBalancesBefore(before: Long): List<WalletBalanceRow>

    @Query(
        """SELECT w.id AS walletId,
             (CASE WHEN w.openedAt < :before THEN w.initialBalance ELSE 0 END)
             + COALESCE((SELECT SUM(CASE t.type WHEN 'INCOME' THEN t.amount ELSE -t.amount END)
                         FROM transactions t WHERE t.walletId = w.id AND t.occurredAt < :before), 0)
             + COALESCE((SELECT SUM(t.amount) FROM transactions t
                         WHERE t.toWalletId = w.id AND t.type = 'TRANSFER' AND t.occurredAt < :before), 0) AS balance
           FROM wallets w""",
    )
    fun observeBalancesBefore(before: Long): Flow<List<WalletBalanceRow>>

    // ---------- Metadata data & retensi ----------
    @Query("SELECT COUNT(*) FROM transactions")
    fun observeCount(): Flow<Int>

    /** Berubah setiap ada tambah/ubah/hapus transaksi. Dipakai sebagai pemicu hitung ulang statistik. */
    @Query("SELECT COALESCE(MAX(updatedAt), 0) + COUNT(*) FROM transactions")
    fun observeVersion(): Flow<Long>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    @Query("SELECT MIN(occurredAt) FROM transactions")
    fun observeOldestAt(): Flow<Long?>

    @Query("SELECT MIN(occurredAt) FROM transactions")
    suspend fun getOldestAt(): Long?

    @Query("SELECT MAX(occurredAt) FROM transactions")
    suspend fun getNewestAt(): Long?

    @Query("SELECT COUNT(*) FROM transactions WHERE occurredAt < :before")
    suspend fun countBefore(before: Long): Int

    @Query("SELECT * FROM transactions WHERE occurredAt < :before ORDER BY occurredAt ASC, id ASC")
    suspend fun getBefore(before: Long): List<TransactionEntity>

    @Query("DELETE FROM transactions WHERE occurredAt < :before")
    suspend fun deleteBefore(before: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE walletId = :walletId OR toWalletId = :walletId")
    suspend fun countForWallet(walletId: Long): Int

    @Query("SELECT DISTINCT tags FROM transactions WHERE tags != ''")
    suspend fun getDistinctTagStrings(): List<String>

    /** Menyisipkan kembali transaksi dengan id aslinya (fitur Urungkan hapus). */
    @Insert(onConflict = androidx.room.OnConflictStrategy.ABORT)
    suspend fun insertWithId(entity: TransactionEntity): Long

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}
