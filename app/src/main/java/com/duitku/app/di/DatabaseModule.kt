package com.duitku.app.di

import android.content.Context
import androidx.room.Room
import com.duitku.app.data.local.DuitKuDatabase
import com.duitku.app.data.local.DuitKuMigrations
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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DuitKuDatabase =
        Room.databaseBuilder(context, DuitKuDatabase::class.java, DuitKuDatabase.NAME)
            // Sengaja TIDAK memakai fallbackToDestructiveMigration: data keuangan tidak boleh hilang.
            .addMigrations(*DuitKuMigrations.ALL)
            .build()

    @Provides fun provideWalletDao(db: DuitKuDatabase): WalletDao = db.walletDao()
    @Provides fun provideCategoryDao(db: DuitKuDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideTransactionDao(db: DuitKuDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideBudgetDao(db: DuitKuDatabase): BudgetDao = db.budgetDao()
    @Provides fun provideRecurringDao(db: DuitKuDatabase): RecurringTransactionDao = db.recurringDao()
    @Provides fun provideSavingGoalDao(db: DuitKuDatabase): SavingGoalDao = db.savingGoalDao()
    @Provides fun provideDebtDao(db: DuitKuDatabase): DebtDao = db.debtDao()
    @Provides fun provideStatementDao(db: DuitKuDatabase): StatementDao = db.statementDao()
    @Provides fun provideAuditLogDao(db: DuitKuDatabase): AuditLogDao = db.auditLogDao()
    @Provides fun provideExportHistoryDao(db: DuitKuDatabase): ExportHistoryDao = db.exportHistoryDao()
    @Provides fun provideDataTransferDao(db: DuitKuDatabase): DataTransferDao = db.dataTransferDao()
    @Provides fun provideBackupRecordDao(db: DuitKuDatabase): BackupRecordDao = db.backupRecordDao()
}
