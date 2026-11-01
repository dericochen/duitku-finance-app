package com.duitku.app.data.repository

import androidx.room.withTransaction
import com.duitku.app.core.util.PeriodCalculator
import com.duitku.app.core.util.toKey
import com.duitku.app.core.util.toYearMonth
import com.duitku.app.data.local.DuitKuDatabase
import com.duitku.app.data.local.dao.AuditLogDao
import com.duitku.app.data.local.dao.CategoryDao
import com.duitku.app.data.local.dao.DebtDao
import com.duitku.app.data.local.dao.RecurringTransactionDao
import com.duitku.app.data.local.dao.SavingGoalDao
import com.duitku.app.data.local.dao.StatementDao
import com.duitku.app.data.local.dao.StatementSummaryRow
import com.duitku.app.data.local.dao.TransactionDao
import com.duitku.app.data.local.dao.WalletDao
import com.duitku.app.data.local.entity.AuditLogEntity
import com.duitku.app.data.local.entity.MonthlyStatementEntity
import com.duitku.app.data.local.entity.MonthlyWalletBalanceEntity
import com.duitku.app.data.mapper.toDomain
import com.duitku.app.domain.model.AuditEntry
import com.duitku.app.domain.model.CategoryKind
import com.duitku.app.domain.model.CloseResult
import com.duitku.app.domain.model.LiveMonth
import com.duitku.app.domain.model.ReopenResult
import com.duitku.app.domain.model.SnapBudget
import com.duitku.app.domain.model.SnapDebt
import com.duitku.app.domain.model.SnapGoal
import com.duitku.app.domain.model.SnapPrevious
import com.duitku.app.domain.model.SnapRecurring
import com.duitku.app.domain.model.StatementSnapshot
import com.duitku.app.domain.model.StatementStatus
import com.duitku.app.domain.model.StatementSummary
import com.duitku.app.domain.model.VerifyResult
import com.duitku.app.domain.repository.BudgetRepository
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.domain.repository.StatementRepository
import com.duitku.app.domain.usecase.statement.Checksum
import com.duitku.app.domain.usecase.statement.DocumentNumber
import com.duitku.app.domain.usecase.statement.LiveMonthBuilder
import com.duitku.app.domain.usecase.statement.PeriodMath
import com.duitku.app.domain.usecase.statement.SnapshotFactory
import com.duitku.app.domain.usecase.statement.SnapshotInput
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@Singleton
class StatementRepositoryImpl @Inject constructor(
    private val database: DuitKuDatabase,
    private val statementDao: StatementDao,
    private val transactionDao: TransactionDao,
    private val walletDao: WalletDao,
    private val categoryDao: CategoryDao,
    private val auditLogDao: AuditLogDao,
    private val savingGoalDao: SavingGoalDao,
    private val debtDao: DebtDao,
    private val recurringDao: RecurringTransactionDao,
    private val budgetRepository: BudgetRepository,
    private val settingsRepository: SettingsRepository,
) : StatementRepository {

    // ---------- Tampilan langsung ----------

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeMonth(periodYm: Int, walletId: Long?): Flow<LiveMonth> =
        statementDao.observeActiveSummary(periodYm)
            .map { it?.takeIf { s -> s.status == StatementStatus.CLOSED }?.id }
            .distinctUntilChanged()
            .flatMapLatest { closedId ->
                // Bulan tertutup dibaca dari snapshot arsip: tidak berubah dan tetap utuh setelah retensi membersihkan transaksi mentah.
                if (closedId != null) {
                    kotlinx.coroutines.flow.flow {
                        val snapshot = SnapshotFactory.decode(statementDao.getById(closedId)!!.snapshotJson)
                        emit(LiveMonthBuilder.fromSnapshot(snapshot, walletId))
                    }
                } else {
                    liveMonth(periodYm, walletId)
                }
            }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun liveMonth(periodYm: Int, walletId: Long?): Flow<LiveMonth> =
        settingsRepository.settings.map { it.monthStartDay }.distinctUntilChanged().flatMapLatest { day ->
            val range = PeriodCalculator.rangeOf(periodYm.toYearMonth(), day)
            val start = range.startMillis()
            val end = range.endMillis()
            combine(
                transactionDao.observeBetween(start, end),
                walletDao.observeAll(),
                transactionDao.observeBalancesBefore(start),
                categoryDao.observeAll(),
                transactionDao.observeBalancesBefore(end + 1),
            ) { txsDesc, wallets, before, categories, atEnd ->
                LiveMonthBuilder.build(
                    periodYm = periodYm, startMillis = start, endMillis = end, walletId = walletId,
                    txsAscending = txsDesc.asReversed().map { it.toDomain() },
                    wallets = wallets.map { it.toDomain() },
                    balancesBefore = before.associate { it.walletId to it.balance },
                    categories = categories.map { it.toDomain() },
                    balancesAtEnd = atEnd.associate { it.walletId to it.balance },
                )
            }
        }

    // ---------- Arsip ----------

    override fun observeSummaries(): Flow<List<StatementSummary>> =
        statementDao.observeLatestSummaries().map { list -> list.map { it.toDomain() } }

    override fun observeActive(periodYm: Int): Flow<StatementSummary?> =
        statementDao.observeActiveSummary(periodYm).map { it?.toDomain() }

    override suspend fun getActive(periodYm: Int): StatementSummary? =
        statementDao.getActive(periodYm)?.toSummary()

    override suspend fun getSummaryById(id: Long): StatementSummary? = statementDao.getById(id)?.toSummary()

    override fun observeClosedCount(): Flow<Int> = statementDao.observeClosedCount()

    override suspend fun close(periodYm: Int): CloseResult = try {
        database.withTransaction<CloseResult> {
            val active = statementDao.getActive(periodYm)
            if (active?.status == StatementStatus.CLOSED) return@withTransaction CloseResult.AlreadyClosed

            val snapshot = buildLiveSnapshot(periodYm)
            if (snapshot.totals.txCount == 0) return@withTransaction CloseResult.NoData

            val json = SnapshotFactory.encode(snapshot)
            val checksum = Checksum.sha256Hex(json)
            val revision = statementDao.maxRevision(periodYm) + 1
            var sequence = revision
            var document = DocumentNumber.format(periodYm, sequence)
            while (statementDao.countDocumentNumber(document) > 0) {
                sequence++
                document = DocumentNumber.format(periodYm, sequence)
            }
            // Revisi yang dibuka kembali digantikan revisi baru; datanya tetap tersimpan sebagai riwayat.
            if (active != null) statementDao.updateStatus(active.id, StatementStatus.SUPERSEDED)

            val now = System.currentTimeMillis()
            val entity = MonthlyStatementEntity(
                periodYm = periodYm, startDate = snapshot.startMillis, endDate = snapshot.endMillis,
                openingBalance = snapshot.totals.opening, totalIncome = snapshot.totals.income,
                totalExpense = snapshot.totals.expense, totalTransfer = snapshot.totals.transfer,
                closingBalance = snapshot.totals.closing, netChange = snapshot.totals.net,
                transactionCount = snapshot.totals.txCount, status = StatementStatus.CLOSED, closedAt = now,
                revision = revision, documentNumber = document, snapshotJson = json, checksum = checksum,
                userNote = active?.userNote.orEmpty(), createdAt = now,
            )
            val id = statementDao.insert(entity)
            statementDao.insertWalletBalances(
                snapshot.wallets.map {
                    MonthlyWalletBalanceEntity(
                        statementId = id, periodYm = periodYm, walletId = it.id, walletName = it.name,
                        openingBalance = it.opening, totalIncome = it.income, totalExpense = it.expense,
                        transferIn = it.transferIn, transferOut = it.transferOut, closingBalance = it.closing,
                    )
                },
            )
            audit(
                periodYm, "STATEMENT", id, "CLOSE",
                "Bulan ditutup: $document (Rev.$revision), ${snapshot.totals.txCount} transaksi",
                null, "checksum=${Checksum.short(checksum)}",
            )
            CloseResult.Closed(entity.copy(id = id).toSummary())
        }
    } catch (e: Exception) {
        CloseResult.Failed(e.message ?: "Gagal menutup bulan")
    }

    override suspend fun reopen(periodYm: Int, reason: String): ReopenResult = try {
        database.withTransaction<ReopenResult> {
            val active = statementDao.getActive(periodYm)
            if (active == null || active.status != StatementStatus.CLOSED) return@withTransaction ReopenResult.NotClosed
            statementDao.updateStatus(active.id, StatementStatus.OPEN)
            audit(
                periodYm, "STATEMENT", active.id, "REOPEN",
                "Bulan dibuka kembali: ${reason.ifBlank { "tanpa alasan" }}",
                "CLOSED ${active.documentNumber}", "OPEN",
            )
            val later = statementDao.getClosedPeriods().filter { it > periodYm }.sorted()
            ReopenResult.Reopened(periodYm, later)
        }
    } catch (e: Exception) {
        ReopenResult.Failed(e.message ?: "Gagal membuka bulan")
    }

    override suspend fun verify(statementId: Long): VerifyResult {
        val entity = statementDao.getById(statementId) ?: return VerifyResult.NotFound
        val actual = Checksum.sha256Hex(entity.snapshotJson)
        return if (actual == entity.checksum) VerifyResult.Valid(actual) else VerifyResult.Tampered(entity.checksum, actual)
    }

    override suspend fun getSnapshot(statementId: Long): StatementSnapshot? =
        statementDao.getById(statementId)?.let { SnapshotFactory.decode(it.snapshotJson) }

    override suspend fun updateNote(statementId: Long, note: String) = statementDao.updateNote(statementId, note.take(500))

    override suspend fun periodsDueForAutoClose(graceDays: Int, today: LocalDate): List<Int> {
        val oldest = transactionDao.getOldestAt() ?: return emptyList()
        val startDay = settingsRepository.current().monthStartDay
        val first = PeriodCalculator.periodOfMillis(oldest, startDay)
        val last = PeriodCalculator.periodOf(today, startDay)
        return PeriodCalculator.monthsBetween(first, last).filter { ym ->
            val range = PeriodCalculator.rangeOf(ym, startDay)
            range.endInclusive.plusDays(graceDays.toLong()).isBefore(today) &&
                statementDao.getActive(ym.toKey()) == null &&
                transactionDao.getTypeTotals(range.startMillis(), range.endMillis()).sumOf { it.txCount } > 0
        }.map { it.toKey() }
    }

    override fun observeAudit(limit: Int): Flow<List<AuditEntry>> =
        auditLogDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override fun observeAuditForPeriod(periodYm: Int): Flow<List<AuditEntry>> =
        auditLogDao.observeForPeriod(periodYm).map { list -> list.map { it.toDomain() } }

    // ---------- Perakitan snapshot ----------

    override suspend fun buildLiveSnapshot(periodYm: Int): StatementSnapshot {
        val settings = settingsRepository.current()
        val startDay = settings.monthStartDay
        val range = PeriodCalculator.rangeOf(periodYm.toYearMonth(), startDay)
        val start = range.startMillis()
        val end = range.endMillis()

        val wallets = walletDao.getAll().map { it.toDomain() }
        val categories = categoryDao.getAll().map { it.toDomain() }
        val txs = transactionDao.getBetweenAscending(start, end).map { it.toDomain() }
        val before = transactionDao.getBalancesBefore(start).associate { it.walletId to it.balance }
        val atEnd = transactionDao.getBalancesBefore(end + 1).associate { it.walletId to it.balance }
        val flows = PeriodMath.walletFlows(wallets, before, txs, start, end, atEnd)

        val budgets = budgetRepository.getLines(periodYm).map {
            SnapBudget(it.budget.categoryId, it.budget.amount, it.rollIn, it.spent, it.budget.rollover)
        }
        val goals = savingGoalDao.getAll().filter { it.createdAt <= end }.map { g ->
            val saved = savingGoalDao.getContributions(g.id).filter { it.contributedAt <= end }.sumOf { it.amount }
            SnapGoal(g.id, g.name, g.targetAmount, saved, g.deadline)
        }
        val debts = debtDao.getAll().filter { it.debt.createdAt <= end }.mapNotNull { row ->
            val d = row.debt
            if (d.paidOffAt != null && d.paidOffAt <= end) return@mapNotNull null
            val paid = debtDao.getPayments(d.id).filter { it.paidAt <= end }.sumOf { it.amount }
            SnapDebt(d.id, d.kind, d.partyName, d.principalAmount, paid, d.dueDate)
        }
        val recurring = recurringDao.getAll().map {
            SnapRecurring(it.title, it.type, it.amount, it.frequency, it.intervalCount, it.isActive)
        }

        val prevRange = PeriodCalculator.rangeOf(YearMonth.of(periodYm / 100, periodYm % 100).minusMonths(1), startDay)
        val prevTxs = transactionDao.getBetweenAscending(prevRange.startMillis(), prevRange.endMillis()).map { it.toDomain() }
        val previous = if (prevTxs.isEmpty()) {
            SnapPrevious()
        } else {
            val prevBefore = transactionDao.getBalancesBefore(prevRange.startMillis()).associate { it.walletId to it.balance }
            val prevAtEnd = transactionDao.getBalancesBefore(prevRange.endMillis() + 1).associate { it.walletId to it.balance }
            val prevFlows = PeriodMath.walletFlows(wallets, prevBefore, prevTxs, prevRange.startMillis(), prevRange.endMillis(), prevAtEnd)
            SnapPrevious(
                totals = PeriodMath.totals(prevTxs, prevFlows),
                categoryExpense = PeriodMath.categoryTotals(prevTxs, CategoryKind.EXPENSE),
                categoryIncome = PeriodMath.categoryTotals(prevTxs, CategoryKind.INCOME),
            )
        }

        return SnapshotFactory.build(
            SnapshotInput(
                periodYm = periodYm, startMillis = start, endMillis = end, startDay = startDay,
                currency = settings.currencyCode, owner = settings.ownerName, wallets = flows,
                categories = categories, transactions = txs, budgets = budgets, goals = goals,
                debts = debts, recurring = recurring, previous = previous,
            ),
        )
    }

    private suspend fun audit(
        periodYm: Int,
        entityType: String,
        entityId: Long?,
        action: String,
        description: String,
        old: String?,
        new: String?,
    ) {
        auditLogDao.insert(
            AuditLogEntity(
                createdAt = System.currentTimeMillis(), periodYm = periodYm, entityType = entityType,
                entityId = entityId, action = action, description = description, oldValue = old, newValue = new,
            ),
        )
    }

    private fun StatementSummaryRow.toDomain() = StatementSummary(
        id, periodYm, startDate, endDate, openingBalance, totalIncome, totalExpense, totalTransfer,
        closingBalance, netChange, transactionCount, status, closedAt, revision, documentNumber, checksum, userNote,
    )

    private fun MonthlyStatementEntity.toSummary() = StatementSummary(
        id, periodYm, startDate, endDate, openingBalance, totalIncome, totalExpense, totalTransfer,
        closingBalance, netChange, transactionCount, status, closedAt, revision, documentNumber, checksum, userNote,
    )

    private fun AuditLogEntity.toDomain() = AuditEntry(
        id, createdAt, periodYm, entityType, entityId, action, description, oldValue, newValue,
    )
}
