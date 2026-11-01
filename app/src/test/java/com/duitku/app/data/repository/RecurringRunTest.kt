package com.duitku.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.duitku.app.core.util.toEpochMillis
import com.duitku.app.data.local.DuitKuDatabase
import com.duitku.app.data.local.entity.CategoryEntity
import com.duitku.app.data.local.entity.RecurringTransactionEntity
import com.duitku.app.data.local.entity.WalletEntity
import com.duitku.app.domain.model.AppSettings
import com.duitku.app.domain.model.CategoryKind
import com.duitku.app.domain.model.CloseResult
import com.duitku.app.domain.model.DebtKind
import com.duitku.app.domain.model.RecurrenceFrequency
import com.duitku.app.domain.model.TransactionType
import com.duitku.app.domain.model.UseCaseResult
import com.duitku.app.domain.model.WalletType
import com.duitku.app.domain.repository.BudgetAlerts
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.domain.model.Debt
import com.duitku.app.domain.model.SavingGoal
import com.duitku.app.domain.usecase.planning.AddDebtPaymentUseCase
import com.duitku.app.domain.usecase.planning.AddGoalContributionUseCase
import com.duitku.app.domain.usecase.planning.RunDueRecurringUseCase
import com.duitku.app.domain.usecase.transaction.SaveTransactionUseCase
import com.duitku.app.domain.usecase.transaction.ValidateTransactionUseCase
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecurringRunTest {

    private lateinit var db: DuitKuDatabase
    private lateinit var recurring: RecurringRepositoryImpl
    private lateinit var statements: StatementRepositoryImpl
    private lateinit var runDue: RunDueRecurringUseCase
    private var wallet = 0L
    private var category = 0L

    private val settings = object : SettingsRepository {
        private val s = MutableStateFlow(AppSettings(onboardingDone = true))
        override val settings: Flow<AppSettings> = s
        override suspend fun current() = s.value
        override suspend fun update(transform: (AppSettings) -> AppSettings) { s.value = transform(s.value) }
    }

    private fun dt(y: Int, m: Int, d: Int, h: Int = 8) = LocalDateTime.of(y, m, d, h, 0)

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DuitKuDatabase::class.java)
            .allowMainThreadQueries().build()
        val budgets = BudgetRepositoryImpl(db.budgetDao(), db.transactionDao(), settings)
        val transactions = TransactionRepositoryImpl(db, db.transactionDao(), db.statementDao(), db.auditLogDao(), settings)
        statements = StatementRepositoryImpl(
            db, db.statementDao(), db.transactionDao(), db.walletDao(), db.categoryDao(), db.auditLogDao(),
            db.savingGoalDao(), db.debtDao(), db.recurringDao(), budgets, settings,
        )
        recurring = RecurringRepositoryImpl(db.recurringDao())
        val noAlerts = object : BudgetAlerts { override suspend fun check(occurredAt: Long) = Unit }
        runDue = RunDueRecurringUseCase(recurring, SaveTransactionUseCase(transactions, ValidateTransactionUseCase(), noAlerts))

        wallet = db.walletDao().insert(WalletEntity(name = "W", type = WalletType.BANK, initialBalance = 0, iconKey = "bank", colorArgb = 0, createdAt = 0, openedAt = 0))
        category = db.categoryDao().insert(CategoryEntity(name = "Tagihan", kind = CategoryKind.EXPENSE, iconKey = "bill", colorArgb = 0))
        Unit
    }

    @After
    fun tearDown() = db.close()

    private suspend fun addRule(start: LocalDateTime, nextDue: LocalDateTime, frequency: RecurrenceFrequency, end: LocalDateTime? = null): Long =
        db.recurringDao().insert(
            RecurringTransactionEntity(
                title = "Langganan", type = TransactionType.EXPENSE, amount = 50_000, walletId = wallet, categoryId = category,
                frequency = frequency, startAt = start.toEpochMillis(), endAt = end?.toEpochMillis(),
                nextDueAt = nextDue.toEpochMillis(), createdAt = 0,
            ),
        )

    @Test
    fun `jadwal terlewat dicatat semuanya lalu jadwal maju dan aman diulang`() = runBlocking {
        val id = addRule(dt(2026, 11, 1), dt(2026, 11, 1), RecurrenceFrequency.DAILY)
        val now = dt(2026, 11, 4, 9)
        val first = runDue(now)
        assertEquals(4, first.created)
        assertEquals(4, db.transactionDao().count())
        assertTrue(db.transactionDao().getAllAscending().all { it.recurringId == id })
        assertEquals(dt(2026, 11, 5).toEpochMillis(), db.recurringDao().getById(id)!!.nextDueAt)

        // Dijalankan lagi pada waktu yang sama: tidak ada duplikat.
        assertEquals(0, runDue(now).created)
        assertEquals(4, db.transactionDao().count())
    }

    @Test
    fun `aturan berakhir dinonaktifkan setelah kemunculan terakhir`() = runBlocking {
        val id = addRule(dt(2026, 9, 15), dt(2026, 9, 15), RecurrenceFrequency.MONTHLY, end = dt(2026, 11, 20))
        val result = runDue(dt(2026, 12, 31))
        assertEquals(3, result.created) // 15 Sep, 15 Okt, 15 Nov
        assertFalse(db.recurringDao().getById(id)!!.isActive)
    }

    @Test
    fun `bulan tertutup dilewati dan dihitung bukan hilang diam-diam`() = runBlocking {
        // Satu transaksi manual di Oktober supaya bulan bisa ditutup.
        db.transactionDao().insert(
            com.duitku.app.data.local.entity.TransactionEntity(
                type = TransactionType.INCOME, amount = 1_000, walletId = wallet, occurredAt = dt(2026, 10, 2).toEpochMillis(),
                createdAt = 0, updatedAt = 0,
            ),
        )
        assertTrue(statements.close(202610) is CloseResult.Closed)
        val id = addRule(dt(2026, 10, 10), dt(2026, 10, 10), RecurrenceFrequency.MONTHLY)
        val result = runDue(dt(2026, 11, 15))
        assertEquals(1, result.skippedClosed)  // 10 Okt jatuh di bulan tertutup
        assertEquals(1, result.created)        // 10 Nov tercatat
        assertEquals(dt(2026, 12, 10).toEpochMillis(), db.recurringDao().getById(id)!!.nextDueAt)
    }

    @Test
    fun `aturan nonaktif tidak dijalankan dan aturan baru tidak mengisi mundur`() = runBlocking {
        val id = addRule(dt(2026, 11, 1), dt(2026, 11, 1), RecurrenceFrequency.DAILY)
        recurring.setActive(id, false)
        assertEquals(0, runDue(dt(2026, 11, 4)).created)

        // Simpan lewat repository: jadwal dimulai dari kemunculan pertama >= sekarang, bukan masa lalu.
        val saved = recurring.save(
            com.duitku.app.domain.model.RecurringTransaction(
                title = "Baru", type = TransactionType.EXPENSE, amount = 1_000, walletId = wallet, categoryId = category,
                frequency = RecurrenceFrequency.DAILY, startAt = dt(2020, 1, 1).toEpochMillis(), nextDueAt = 0,
            ),
        )
        assertTrue(recurring.get(saved)!!.nextDueAt >= System.currentTimeMillis() - 1_000)
    }

    @Test
    fun `setoran target menambah total dan menandai selesai`() = runBlocking {
        val goals = GoalRepositoryImpl(db, db.savingGoalDao())
        val id = goals.save(SavingGoal(name = "Laptop", targetAmount = 1_000_000))
        val add = AddGoalContributionUseCase(goals)
        var goal = goals.observeGoals().let { db.savingGoalDao().getById(id)!! }
        val g = SavingGoal(id = id, name = "Laptop", targetAmount = 1_000_000, savedAmount = goal.savedAmount)

        val r1 = add(g, 400_000, "awal", 1L) as UseCaseResult.Success
        assertEquals(400_000, r1.value.savedAmount)
        assertEquals(null, r1.value.completedAt)
        assertTrue(add(r1.value, -500_000, "", 2L) is UseCaseResult.Failure) // tarik melebihi saldo
        assertTrue(add(r1.value, 0, "", 2L) is UseCaseResult.Failure)
        val r2 = add(r1.value, 600_000, "", 3L) as UseCaseResult.Success
        assertEquals(1_000_000, r2.value.savedAmount)
        assertTrue(r2.value.completedAt != null)
        assertEquals(2, goals.getContributions(id).size)
    }

    @Test
    fun `pembayaran utang otomatis lunas dan tidak boleh melebihi sisa`() = runBlocking {
        val debts = DebtRepositoryImpl(db, db.debtDao())
        val id = debts.save(Debt(kind = DebtKind.DEBT, partyName = "Budi", principalAmount = 1_000_000))
        val pay = AddDebtPaymentUseCase(debts)
        val first = pay(debts.get(id)!!, 600_000, "", 1L) as UseCaseResult.Success
        assertFalse(first.value.debt.isPaidOff)
        assertEquals(400_000, first.value.remaining)
        assertTrue(pay(first.value, 500_000, "", 2L) is UseCaseResult.Failure)
        val last = pay(first.value, 400_000, "", 3L) as UseCaseResult.Success
        assertTrue(last.value.debt.isPaidOff)
        assertTrue(pay(last.value, 1, "", 4L) is UseCaseResult.Failure)
    }
}
