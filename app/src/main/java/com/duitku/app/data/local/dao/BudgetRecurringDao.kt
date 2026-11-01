package com.duitku.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.duitku.app.data.local.entity.BudgetEntity
import com.duitku.app.data.local.entity.RecurringTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Insert
    suspend fun insert(entity: BudgetEntity): Long

    @Insert
    suspend fun insertAll(entities: List<BudgetEntity>)

    @Update
    suspend fun update(entity: BudgetEntity)

    @Delete
    suspend fun delete(entity: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM budgets WHERE periodYm = :periodYm ORDER BY categoryId IS NOT NULL, id")
    fun observeForPeriod(periodYm: Int): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE periodYm = :periodYm ORDER BY categoryId IS NOT NULL, id")
    suspend fun getForPeriod(periodYm: Int): List<BudgetEntity>

    /** Cari anggaran satu kategori; [categoryId] null berarti anggaran total. */
    @Query(
        """SELECT * FROM budgets WHERE periodYm = :periodYm
           AND ((:categoryId IS NULL AND categoryId IS NULL) OR categoryId = :categoryId) LIMIT 1""",
    )
    suspend fun find(periodYm: Int, categoryId: Long?): BudgetEntity?

    @Query("SELECT COUNT(*) FROM budgets WHERE periodYm = :periodYm")
    suspend fun countForPeriod(periodYm: Int): Int

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()
}

@Dao
interface RecurringTransactionDao {
    @Insert
    suspend fun insert(entity: RecurringTransactionEntity): Long

    @Update
    suspend fun update(entity: RecurringTransactionEntity)

    @Delete
    suspend fun delete(entity: RecurringTransactionEntity)

    @Query("SELECT * FROM recurring_transactions ORDER BY isActive DESC, nextDueAt ASC")
    fun observeAll(): Flow<List<RecurringTransactionEntity>>

    @Query("SELECT * FROM recurring_transactions ORDER BY isActive DESC, nextDueAt ASC")
    suspend fun getAll(): List<RecurringTransactionEntity>

    @Query("SELECT * FROM recurring_transactions WHERE id = :id")
    suspend fun getById(id: Long): RecurringTransactionEntity?

    /** Jadwal yang jatuh tempo (termasuk yang terlewat saat perangkat mati) untuk di-catch-up. */
    @Query("SELECT * FROM recurring_transactions WHERE isActive = 1 AND nextDueAt <= :now ORDER BY nextDueAt ASC")
    suspend fun getDue(now: Long): List<RecurringTransactionEntity>

    @Query("SELECT * FROM recurring_transactions WHERE isActive = 1")
    suspend fun getActive(): List<RecurringTransactionEntity>

    @Query("DELETE FROM recurring_transactions")
    suspend fun deleteAll()
}
