package com.duitku.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.duitku.app.data.local.dao.AuditLogDao
import com.duitku.app.data.local.dao.BackupRecordDao
import com.duitku.app.data.local.dao.BudgetDao
import com.duitku.app.data.local.dao.CategoryDao
import com.duitku.app.data.local.dao.DataTransferDao
import com.duitku.app.data.local.dao.DebtDao
import com.duitku.app.data.local.dao.ExportHistoryDao
import com.duitku.app.data.local.dao.RecurringTransactionDao
import com.duitku.app.data.local.dao.SavingGoalDao
import com.duitku.app.data.local.dao.StatementDao
import com.duitku.app.data.local.dao.TransactionDao
import com.duitku.app.data.local.dao.WalletDao
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

/**
 * Database utama DuitKu (offline-first).
 *
 * Aturan perubahan skema:
 *  1. Naikkan [VERSION], tambahkan `Migration` di [DuitKuMigrations].
 *  2. Jangan pernah memakai fallbackToDestructiveMigration.
 *  3. Commit file schemas/<version>.json hasil build agar MigrationTest dapat memverifikasinya.
 */
@Database(
    entities = [
        WalletEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        RecurringTransactionEntity::class,
        SavingGoalEntity::class,
        GoalContributionEntity::class,
        DebtEntity::class,
        DebtPaymentEntity::class,
        MonthlyStatementEntity::class,
        MonthlyWalletBalanceEntity::class,
        AuditLogEntity::class,
        ExportHistoryEntity::class,
        BackupRecordEntity::class,
    ],
    version = DuitKuDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class DuitKuDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringDao(): RecurringTransactionDao
    abstract fun savingGoalDao(): SavingGoalDao
    abstract fun debtDao(): DebtDao
    abstract fun statementDao(): StatementDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun exportHistoryDao(): ExportHistoryDao
    abstract fun backupRecordDao(): BackupRecordDao
    abstract fun dataTransferDao(): DataTransferDao

    companion object {
        const val VERSION = 1
        const val NAME = "duitku.db"
    }
}
