package com.duitku.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.duitku.app.data.local.entity.AuditLogEntity
import com.duitku.app.data.local.entity.BackupRecordEntity
import com.duitku.app.data.local.entity.BudgetEntity
import com.duitku.app.data.local.entity.CategoryEntity
import com.duitku.app.data.local.entity.DebtEntity
import com.duitku.app.data.local.entity.DebtPaymentEntity
import com.duitku.app.data.local.entity.ExportHistoryEntity
import com.duitku.app.data.local.entity.GoalContributionEntity
import com.duitku.app.data.local.entity.MonthlyStatementEntity
import com.duitku.app.data.local.entity.MonthlyWalletBalanceEntity
import com.duitku.app.data.local.entity.RecurringTransactionEntity
import com.duitku.app.data.local.entity.SavingGoalEntity
import com.duitku.app.data.local.entity.TransactionEntity
import com.duitku.app.data.local.entity.WalletEntity

/** Akses massal semua tabel untuk backup, restore, retensi, dan reset. Hanya dipanggil dalam transaksi database. */
@Dao
interface DataTransferDao {
    @Query("SELECT * FROM wallets ORDER BY id") suspend fun wallets(): List<WalletEntity>
    @Query("SELECT * FROM categories ORDER BY (parentId IS NOT NULL), id") suspend fun categories(): List<CategoryEntity>
    @Query("SELECT * FROM recurring_transactions ORDER BY id") suspend fun recurring(): List<RecurringTransactionEntity>
    @Query("SELECT * FROM transactions ORDER BY id") suspend fun transactions(): List<TransactionEntity>
    @Query("SELECT * FROM budgets ORDER BY id") suspend fun budgets(): List<BudgetEntity>
    @Query("SELECT * FROM saving_goals ORDER BY id") suspend fun goals(): List<SavingGoalEntity>
    @Query("SELECT * FROM goal_contributions ORDER BY id") suspend fun contributions(): List<GoalContributionEntity>
    @Query("SELECT * FROM debts ORDER BY id") suspend fun debts(): List<DebtEntity>
    @Query("SELECT * FROM debt_payments ORDER BY id") suspend fun payments(): List<DebtPaymentEntity>
    @Query("SELECT * FROM monthly_statements ORDER BY id") suspend fun statements(): List<MonthlyStatementEntity>
    @Query("SELECT * FROM monthly_wallet_balances ORDER BY id") suspend fun walletBalances(): List<MonthlyWalletBalanceEntity>
    @Query("SELECT * FROM audit_logs ORDER BY id") suspend fun auditLogs(): List<AuditLogEntity>
    @Query("SELECT * FROM export_history ORDER BY id") suspend fun exportHistory(): List<ExportHistoryEntity>
    @Query("SELECT * FROM backup_records ORDER BY id") suspend fun backupRecords(): List<BackupRecordEntity>

    // Sisipkan dengan id asli (mode timpa).
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun putWallets(list: List<WalletEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun putCategories(list: List<CategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun putRecurring(list: List<RecurringTransactionEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun putTransactions(list: List<TransactionEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun putBudgets(list: List<BudgetEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun putGoals(list: List<SavingGoalEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun putContributions(list: List<GoalContributionEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun putDebts(list: List<DebtEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun putPayments(list: List<DebtPaymentEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun putStatements(list: List<MonthlyStatementEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun putWalletBalances(list: List<MonthlyWalletBalanceEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun putAuditLogs(list: List<AuditLogEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun putExportHistory(list: List<ExportHistoryEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun putBackupRecords(list: List<BackupRecordEntity>)

    // Sisipkan satu baris dan kembalikan id baru (mode gabung).
    @Insert suspend fun addWallet(e: WalletEntity): Long
    @Insert suspend fun addCategory(e: CategoryEntity): Long
    @Insert suspend fun addTransaction(e: TransactionEntity): Long
    @Insert suspend fun addBudget(e: BudgetEntity): Long
    @Insert suspend fun addGoal(e: SavingGoalEntity): Long
    @Insert suspend fun addContribution(e: GoalContributionEntity): Long
    @Insert suspend fun addDebt(e: DebtEntity): Long
    @Insert suspend fun addPayment(e: DebtPaymentEntity): Long
    @Insert suspend fun addStatement(e: MonthlyStatementEntity): Long
    @Insert suspend fun addWalletBalance(e: MonthlyWalletBalanceEntity): Long
    @Insert suspend fun addAuditLog(e: AuditLogEntity): Long

    @Query("SELECT COUNT(*) FROM monthly_statements WHERE documentNumber = :doc") suspend fun statementCountByDocument(doc: String): Int

    // Hapus semua, urut aman terhadap foreign key.
    @Query("DELETE FROM transactions") suspend fun clearTransactions()
    @Query("DELETE FROM budgets") suspend fun clearBudgets()
    @Query("DELETE FROM recurring_transactions") suspend fun clearRecurring()
    @Query("DELETE FROM goal_contributions") suspend fun clearContributions()
    @Query("DELETE FROM saving_goals") suspend fun clearGoals()
    @Query("DELETE FROM debt_payments") suspend fun clearPayments()
    @Query("DELETE FROM debts") suspend fun clearDebts()
    @Query("DELETE FROM monthly_wallet_balances") suspend fun clearWalletBalances()
    @Query("DELETE FROM monthly_statements") suspend fun clearStatements()
    @Query("DELETE FROM audit_logs") suspend fun clearAuditLogs()
    @Query("DELETE FROM export_history") suspend fun clearExportHistory()
    @Query("DELETE FROM backup_records") suspend fun clearBackupRecords()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM wallets") suspend fun clearWallets()

    // Pembersihan retensi.
    @Query("UPDATE wallets SET initialBalance = :balance, openedAt = :openedAt WHERE id = :id")
    suspend fun carryForward(id: Long, balance: Long, openedAt: Long)
}
