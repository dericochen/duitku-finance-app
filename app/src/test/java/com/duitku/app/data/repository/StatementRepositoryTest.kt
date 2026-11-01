package com.duitku.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.duitku.app.core.util.toEpochMillis
import com.duitku.app.data.local.DuitKuDatabase
import com.duitku.app.data.local.entity.CategoryEntity
import com.duitku.app.data.local.entity.TransactionEntity
import com.duitku.app.data.local.entity.WalletEntity
import com.duitku.app.domain.model.AppSettings
import com.duitku.app.domain.model.CategoryKind
import com.duitku.app.domain.model.CloseResult
import com.duitku.app.domain.model.ReopenResult
import com.duitku.app.domain.model.StatementStatus
import com.duitku.app.domain.model.Transaction
import com.duitku.app.domain.model.TransactionType
import com.duitku.app.domain.model.VerifyResult
import com.duitku.app.domain.model.WalletType
import com.duitku.app.domain.repository.PeriodClosedException
import com.duitku.app.domain.repository.SettingsRepository
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeSettings(initial: AppSettings = AppSettings(onboardingDone = true, ownerName = "Dewi")) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state
    override suspend fun current() = state.value
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}

/** Alur tutup buku, buka kembali, revisi, dan checksum terhadap database sungguhan. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatementRepositoryTest {

    private lateinit var db: DuitKuDatabase
    private lateinit var statements: StatementRepositoryImpl
    private lateinit var transactions: TransactionRepositoryImpl
    private var wallet = 0L
    private var category = 0L

    private fun at(month: Int, day: Int) = LocalDateTime.of(2026, month, day, 10, 0).toEpochMillis()

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DuitKuDatabase::class.java)
            .allowMainThreadQueries().build()
        val settings = FakeSettings()
        val budgets = BudgetRepositoryImpl(db.budgetDao(), db.transactionDao(), settings)
        statements = StatementRepositoryImpl(
            db, db.statementDao(), db.transactionDao(), db.walletDao(), db.categoryDao(), db.auditLogDao(),
            db.savingGoalDao(), db.debtDao(), db.recurringDao(), budgets, settings,
        )
        transactions = TransactionRepositoryImpl(db, db.transactionDao(), db.statementDao(), db.auditLogDao(), settings)

        wallet = db.walletDao().insert(
            WalletEntity(
                name = "Bank", type = WalletType.BANK, initialBalance = 1_000_000, iconKey = "bank",
                colorArgb = 0, createdAt = 0, openedAt = at(1, 1),
            ),
        )
        category = db.categoryDao().insert(CategoryEntity(name = "Makanan", kind = CategoryKind.EXPENSE, iconKey = "food", colorArgb = 0))
        // September: +500.000 -100.000. Oktober: -50.000.
        add(TransactionType.INCOME, 500_000, at(9, 5))
        add(TransactionType.EXPENSE, 100_000, at(9, 20))
        add(TransactionType.EXPENSE, 50_000, at(10, 5))
        Unit
    }

    @After
    fun tearDown() = db.close()

    private suspend fun add(type: TransactionType, amount: Long, time: Long) {
        db.transactionDao().insert(
            TransactionEntity(
                type = type, amount = amount, walletId = wallet, categoryId = if (type == TransactionType.EXPENSE) category else null,
                occurredAt = time, createdAt = time, updatedAt = time,
            ),
        )
    }

    @Test
    fun `tutup bulan membuat snapshot nomor dokumen dan checksum yang valid`() = runBlocking {
        val result = statements.close(202609) as CloseResult.Closed
        val s = result.summary
        assertEquals("DKU-202609-0001", s.documentNumber)
        assertEquals(1, s.revision)
        assertEquals(StatementStatus.CLOSED, s.status)
        assertEquals(1_000_000, s.openingBalance)
        assertEquals(500_000, s.totalIncome)
        assertEquals(100_000, s.totalExpense)
        assertEquals(1_400_000, s.closingBalance)
        assertEquals(2, s.transactionCount)

        val snapshot = statements.getSnapshot(s.id)!!
        assertEquals(2, snapshot.transactions.size)
        assertEquals("Dewi", snapshot.owner)
        assertTrue(statements.verify(s.id) is VerifyResult.Valid)
        assertEquals(1, db.statementDao().getWalletBalances(s.id).size)
    }

    @Test
    fun `menutup bulan dua kali ditolak dan bulan kosong ditolak`() = runBlocking {
        statements.close(202609)
        assertEquals(CloseResult.AlreadyClosed, statements.close(202609))
        assertEquals(CloseResult.NoData, statements.close(202607))
    }

    @Test
    fun `saldo akhir bulan lalu menjadi saldo awal bulan berikutnya`() = runBlocking {
        val closed = (statements.close(202609) as CloseResult.Closed).summary
        val october = statements.observeMonth(202610, null).first()
        assertEquals(closed.closingBalance, october.summary.opening)
        assertEquals(1_350_000, october.summary.closing)
    }

    @Test
    fun `checksum mendeteksi perubahan isi arsip`() = runBlocking {
        val id = (statements.close(202609) as CloseResult.Closed).summary.id
        db.openHelper.writableDatabase.execSQL("UPDATE monthly_statements SET snapshotJson = REPLACE(snapshotJson, '500000', '900000') WHERE id = $id")
        assertTrue(statements.verify(id) is VerifyResult.Tampered)
    }

    @Test
    fun `bulan tertutup menolak tambah ubah dan hapus transaksi`() = runBlocking {
        statements.close(202609)
        val tx = Transaction(type = TransactionType.EXPENSE, amount = 1_000, walletId = wallet, categoryId = category, occurredAt = at(9, 25))
        assertThrows(PeriodClosedException::class.java) { runBlocking { transactions.save(tx) } }
        val existing = db.transactionDao().getAllAscending().first { it.occurredAt == at(9, 20) }
        assertThrows(PeriodClosedException::class.java) { runBlocking { transactions.delete(existing.id) } }
        assertThrows(PeriodClosedException::class.java) {
            runBlocking { transactions.save(Transaction(id = existing.id, type = TransactionType.EXPENSE, amount = 5, walletId = wallet, categoryId = category, occurredAt = at(10, 1))) }
        }
        // Bulan lain tetap bisa diubah.
        transactions.save(tx.copy(occurredAt = at(10, 25)))
        assertEquals(4, db.transactionDao().count())
    }

    @Test
    fun `buka kembali lalu tutup lagi membuat revisi baru dan mencatat audit`() = runBlocking {
        val first = (statements.close(202609) as CloseResult.Closed).summary
        val reopened = statements.reopen(202609, "Koreksi nominal") as ReopenResult.Reopened
        assertTrue(reopened.laterClosedPeriods.isEmpty())
        assertEquals(StatementStatus.OPEN, statements.getActive(202609)!!.status)

        // Setelah dibuka, edit diizinkan dan tercatat di audit log.
        val target = db.transactionDao().getAllAscending().first { it.occurredAt == at(9, 20) }
        transactions.save(
            Transaction(id = target.id, type = TransactionType.EXPENSE, amount = 150_000, walletId = wallet, categoryId = category, occurredAt = target.occurredAt),
        )
        val second = (statements.close(202609) as CloseResult.Closed).summary
        assertEquals(2, second.revision)
        assertEquals("DKU-202609-0002", second.documentNumber)
        assertEquals(1_350_000, second.closingBalance)

        // Revisi lama tetap utuh (tidak berubah), tetapi berstatus SUPERSEDED.
        assertEquals(StatementStatus.SUPERSEDED, db.statementDao().getById(first.id)!!.status)
        assertTrue(statements.verify(first.id) is VerifyResult.Valid)
        assertEquals(1_400_000, statements.getSnapshot(first.id)!!.totals.closing)
        assertEquals(1, statements.observeSummaries().first().count { it.periodYm == 202609 })

        val actions = db.auditLogDao().getAll().map { it.action }
        assertEquals(listOf("CLOSE", "REOPEN", "UPDATE", "CLOSE"), actions)
        val update = db.auditLogDao().getAll().first { it.action == "UPDATE" }
        assertNotNull(update.oldValue)
        assertNotNull(update.newValue)
    }

    @Test
    fun `membuka bulan yang tidak tertutup ditolak`() = runBlocking {
        assertEquals(ReopenResult.NotClosed, statements.reopen(202609, "x"))
    }

    @Test
    fun `peringatan bila ada bulan setelahnya yang sudah tertutup`() = runBlocking {
        statements.close(202609)
        statements.close(202610)
        val result = statements.reopen(202609, "Koreksi") as ReopenResult.Reopened
        assertEquals(listOf(202610), result.laterClosedPeriods)
    }

    @Test
    fun `periode jatuh tempo tutup otomatis hanya yang sudah lewat masa tenggang dan punya data`() = runBlocking {
        val today = java.time.LocalDate.of(2026, 11, 10)
        val due = statements.periodsDueForAutoClose(graceDays = 3, today = today)
        assertEquals(listOf(202609, 202610), due)
        statements.close(202609)
        assertEquals(listOf(202610), statements.periodsDueForAutoClose(3, today))
        // Masih dalam masa tenggang: Oktober belum jatuh tempo pada 2 Nov.
        assertEquals(emptyList<Int>(), statements.periodsDueForAutoClose(3, java.time.LocalDate.of(2026, 11, 2)))
    }
}
