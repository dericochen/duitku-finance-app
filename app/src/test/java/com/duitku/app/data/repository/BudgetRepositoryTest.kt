package com.duitku.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.duitku.app.core.util.toEpochMillis
import com.duitku.app.data.local.DuitKuDatabase
import com.duitku.app.data.local.entity.CategoryEntity
import com.duitku.app.data.local.entity.TransactionEntity
import com.duitku.app.data.local.entity.WalletEntity
import com.duitku.app.domain.model.AppSettings
import com.duitku.app.domain.model.Budget
import com.duitku.app.domain.model.BudgetStatus
import com.duitku.app.domain.model.CategoryKind
import com.duitku.app.domain.model.TransactionType
import com.duitku.app.domain.model.WalletType
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.domain.usecase.budget.BudgetAlertPolicy
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
class BudgetRepositoryTest {

    private lateinit var db: DuitKuDatabase
    private lateinit var repo: BudgetRepositoryImpl
    private var wallet = 0L
    private var food = 0L

    private val settings = object : SettingsRepository {
        private val s = MutableStateFlow(AppSettings(onboardingDone = true))
        override val settings: Flow<AppSettings> = s
        override suspend fun current() = s.value
        override suspend fun update(transform: (AppSettings) -> AppSettings) { s.value = transform(s.value) }
    }

    private fun at(month: Int, day: Int) = LocalDateTime.of(2026, month, day, 9, 0).toEpochMillis()

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DuitKuDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = BudgetRepositoryImpl(db.budgetDao(), db.transactionDao(), settings)
        wallet = db.walletDao().insert(WalletEntity(name = "W", type = WalletType.CASH, initialBalance = 0, iconKey = "cash", colorArgb = 0, createdAt = 0))
        food = db.categoryDao().insert(CategoryEntity(name = "Makanan", kind = CategoryKind.EXPENSE, iconKey = "food", colorArgb = 0))
        spend(at(9, 10), 400_000)
        spend(at(10, 10), 850_000)
        Unit
    }

    @After
    fun tearDown() = db.close()

    private suspend fun spend(time: Long, amount: Long) {
        db.transactionDao().insert(
            TransactionEntity(type = TransactionType.EXPENSE, amount = amount, walletId = wallet, categoryId = food, occurredAt = time, createdAt = time, updatedAt = time),
        )
    }

    @Test
    fun `pemakaian dihitung dari transaksi periode dan status mengikuti persentase`() = runBlocking {
        repo.save(Budget(periodYm = 202610, categoryId = food, amount = 1_000_000))
        repo.save(Budget(periodYm = 202610, categoryId = null, amount = 5_000_000))
        val lines = repo.getLines(202610)
        val cat = lines.first { !it.isTotal }
        assertEquals(850_000, cat.spent)
        assertEquals(85, cat.percent)
        assertEquals(BudgetStatus.WARNING, cat.status)
        assertEquals(850_000, lines.first { it.isTotal }.spent)
    }

    @Test
    fun `rollover dari bulan sebelumnya masuk ke batas bulan ini`() = runBlocking {
        repo.save(Budget(periodYm = 202609, categoryId = food, amount = 1_000_000, rollover = true)) // terpakai 400.000 -> sisa 600.000
        repo.save(Budget(periodYm = 202610, categoryId = food, amount = 1_000_000))
        val line = repo.getLines(202610).single()
        assertEquals(600_000, line.rollIn)
        assertEquals(1_600_000, line.limit)
        assertEquals(750_000, line.remaining)
        assertEquals(BudgetStatus.SAFE, line.status)
    }

    @Test
    fun `menyimpan dua kali untuk kategori sama memperbarui bukan menggandakan`() = runBlocking {
        val id1 = repo.save(Budget(periodYm = 202610, categoryId = food, amount = 100))
        val id2 = repo.save(Budget(periodYm = 202610, categoryId = food, amount = 250, rollover = true))
        assertEquals(id1, id2)
        val saved = repo.get(202610, food)!!
        assertEquals(250, saved.amount)
        assertTrue(saved.rollover)
        assertEquals(1, db.budgetDao().countForPeriod(202610))
    }

    @Test
    fun `salin anggaran dari bulan sebelumnya tidak menimpa yang sudah ada`() = runBlocking {
        repo.save(Budget(periodYm = 202609, categoryId = food, amount = 1_000_000))
        repo.save(Budget(periodYm = 202609, categoryId = null, amount = 4_000_000))
        repo.save(Budget(periodYm = 202610, categoryId = null, amount = 9_000_000))
        assertEquals(1, repo.copyFromPrevious(202610))
        assertEquals(9_000_000, repo.get(202610, null)!!.amount)
        assertEquals(1_000_000, repo.get(202610, food)!!.amount)
        assertEquals(0, repo.copyFromPrevious(202610))
    }

    @Test
    fun `observeLines memancarkan ulang saat transaksi baru dicatat`() = runBlocking {
        repo.save(Budget(periodYm = 202610, categoryId = food, amount = 1_000_000))
        assertEquals(85, repo.observeLines(202610).first().single().percent)
        spend(at(10, 20), 200_000)
        assertEquals(105, repo.observeLines(202610).first().single().percent)
    }

    @Test
    fun `kebijakan notifikasi sekali per level dan reset saat turun`() {
        assertEquals(0, BudgetAlertPolicy.levelOf(79))
        assertEquals(80, BudgetAlertPolicy.levelOf(80))
        assertEquals(80, BudgetAlertPolicy.levelOf(99))
        assertEquals(100, BudgetAlertPolicy.levelOf(100))
        assertTrue(BudgetAlertPolicy.shouldNotify(0, 80))
        assertFalse(BudgetAlertPolicy.shouldNotify(80, 80))
        assertTrue(BudgetAlertPolicy.shouldNotify(80, 100))
        assertFalse(BudgetAlertPolicy.shouldNotify(100, 80)) // turun: tidak memberitahu lagi, level direset oleh pemanggil
        assertFalse(BudgetAlertPolicy.shouldNotify(100, 100))
    }
}
