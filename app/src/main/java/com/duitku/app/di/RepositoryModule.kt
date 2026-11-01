package com.duitku.app.di

import com.duitku.app.data.notification.BudgetAlertNotifier
import com.duitku.app.data.backup.BackupRepositoryImpl
import com.duitku.app.data.export.ExportRepositoryImpl
import com.duitku.app.data.retention.RetentionRepositoryImpl
import com.duitku.app.data.repository.BudgetRepositoryImpl
import com.duitku.app.data.repository.CategoryRepositoryImpl
import com.duitku.app.data.repository.DebtRepositoryImpl
import com.duitku.app.data.repository.GoalRepositoryImpl
import com.duitku.app.data.repository.RecurringRepositoryImpl
import com.duitku.app.data.repository.StatementRepositoryImpl
import com.duitku.app.data.repository.StatsRepositoryImpl
import com.duitku.app.data.repository.TransactionRepositoryImpl
import com.duitku.app.data.repository.WalletRepositoryImpl
import com.duitku.app.data.seed.DemoDataSeeder
import com.duitku.app.domain.repository.BackupRepository
import com.duitku.app.domain.repository.BudgetAlerts
import com.duitku.app.domain.repository.RetentionRepository
import com.duitku.app.export.pdf.AndroidPdfWriter
import com.duitku.app.export.pdf.PdfWriter
import com.duitku.app.domain.repository.BudgetRepository
import com.duitku.app.domain.repository.CategoryRepository
import com.duitku.app.domain.repository.DebtRepository
import com.duitku.app.domain.repository.ExportRepository
import com.duitku.app.domain.repository.GoalRepository
import com.duitku.app.domain.repository.RecurringRepository
import com.duitku.app.domain.repository.StatementRepository
import com.duitku.app.domain.repository.StatsRepository
import com.duitku.app.domain.repository.DemoDataLoader
import com.duitku.app.domain.repository.TransactionRepository
import com.duitku.app.domain.repository.WalletRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindWalletRepository(impl: WalletRepositoryImpl): WalletRepository
    @Binds abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository
    @Binds abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository
    @Binds abstract fun bindStatementRepository(impl: StatementRepositoryImpl): StatementRepository
    @Binds abstract fun bindRecurringRepository(impl: RecurringRepositoryImpl): RecurringRepository
    @Binds abstract fun bindGoalRepository(impl: GoalRepositoryImpl): GoalRepository
    @Binds abstract fun bindDebtRepository(impl: DebtRepositoryImpl): DebtRepository
    @Binds abstract fun bindExportRepository(impl: ExportRepositoryImpl): ExportRepository
    @Binds abstract fun bindPdfWriter(impl: AndroidPdfWriter): PdfWriter
    @Binds abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository
    @Binds abstract fun bindRetentionRepository(impl: RetentionRepositoryImpl): RetentionRepository
    @Binds abstract fun bindStatsRepository(impl: StatsRepositoryImpl): StatsRepository
    @Binds abstract fun bindBudgetAlerts(impl: BudgetAlertNotifier): BudgetAlerts
    @Binds abstract fun bindBudgetRepository(impl: BudgetRepositoryImpl): BudgetRepository
    @Binds abstract fun bindDemoDataLoader(impl: DemoDataSeeder): DemoDataLoader
}
