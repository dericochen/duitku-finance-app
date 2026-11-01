package com.duitku.app.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.duitku.app.data.local.entity.CategoryEntity
import com.duitku.app.data.local.entity.MonthlyStatementEntity
import com.duitku.app.data.local.entity.TransactionEntity
import com.duitku.app.data.local.entity.WalletEntity
import com.duitku.app.data.repository.TransactionFilterQuery
import com.duitku.app.domain.model.CategoryKind
import com.duitku.app.domain.model.StatementStatus
import com.duitku.app.domain.model.TransactionFilter
import com.duitku.app.domain.model.TransactionType
import com.duitku.app.domain.model.WalletType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Menguji query agregat terhadap SQLite sungguhan (in-memory) lewat Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransactionDaoTest {

    private lateinit var db: DuitKuDatabase
    private var walletA = 0L
    private var walletB = 0L
    private var food = 0L

    // Waktu patokan (ms). Selisih 1 jam antar transaksi.
    private val t1 = 1_700_000_000_000L
    private val t2 = t1 + 3_600_000
    private val t3 = t2 + 3_600_000
    private val t4 = t3 + 3_600_000
    private val t5 = t4 + 3_600_000

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DuitKuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        walletA = db.walletDao().insert(wallet("Dompet A", 1_000_000))
        walletB = db.walletDao().insert(wallet("Dompet B", 0))
        food = db.categoryDao().insert(
            CategoryEntity(name = "Makanan", kind = CategoryKind.EXPENSE, iconKey = "food", colorArgb = 0),
        )
        val dao = db.transactionDao()
        dao.insert(tx(TransactionType.INCOME, 500_000, walletA, at = t1, note = "Gaji"))
        dao.insert(tx(TransactionType.EXPENSE, 200_000, walletA, at = t2, category = food, note = "Kopi enak", tags = listOf("kantor")))
        dao.insert(tx(TransactionType.TRANSFER, 300_000, walletA, to = walletB, at = t3, note = "Isi saldo"))
        dao.insert(tx(TransactionType.EXPENSE, 50_000, walletB, at = t4, category = food, note = "Makan siang"))
        Unit
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun wallet(name: String, initial: Long) = WalletEntity(
        name = name, type = WalletType.BANK, initialBalance = initial,
        iconKey = "bank", colorArgb = 0, createdAt = t1, openedAt = t1 - 1,
    )

    private fun tx(
        type: TransactionType,
        amount: Long,
        wallet: Long,
        at: Long,
        to: Long? = null,
        category: Long? = null,
        note: String = "",
        tags: List<String> = emptyList(),
    ) = TransactionEntity(
        type = type, amount = amount, walletId = wallet, toWalletId = to, categoryId = category,
        note = note, tags = tags, occurredAt = at, createdAt = at, updatedAt = at,
    )

    @Test
    fun `saldo dompet dihitung dari saldo awal pemasukan pengeluaran dan transfer`() = runBlocking {
        val balances = db.transactionDao().getWalletBalances().associate { it.walletId to it.balance }
        assertEquals(1_000_000L, balances[walletA])
        assertEquals(250_000L, balances[walletB])
        // Transfer netral terhadap total seluruh dompet: 1.000.000 + 500.000 - 200.000 - 50.000.
        assertEquals(1_250_000L, balances.values.sum())
    }

    @Test
    fun `observeWalletBalances ikut berubah saat transaksi ditambah`() = runBlocking {
        val before = db.transactionDao().observeWalletBalances().first().associate { it.walletId to it.balance }
        assertEquals(1_000_000L, before[walletA])
        db.transactionDao().insert(tx(TransactionType.EXPENSE, 100_000, walletA, at = t5, category = food))
        val after = db.transactionDao().observeWalletBalances().first().associate { it.walletId to it.balance }
        assertEquals(900_000L, after[walletA])
    }

    private suspend fun balancesBefore(before: Long) =
        db.transactionDao().getBalancesBefore(before).associate { it.walletId to it.balance }

    @Test
    fun `saldo awal periode adalah saldo seluruh dompet sebelum waktu tersebut`() = runBlocking {
        // Sebelum transaksi pertama: hanya saldo awal dompet (openedAt = t1 - 1).
        assertEquals(1_000_000L, balancesBefore(t1)[walletA])
        assertEquals(0L, balancesBefore(t1)[walletB])
        // Sebelum transfer (t3): A = 1.000.000 + 500.000 - 200.000
        assertEquals(1_300_000L, balancesBefore(t3)[walletA])
        // Setelah semua: sama dengan saldo sekarang.
        assertEquals(1_000_000L, balancesBefore(t5)[walletA])
        assertEquals(250_000L, balancesBefore(t5)[walletB])
    }

    @Test
    fun `total seluruh dompet tidak berubah oleh transfer`() = runBlocking {
        assertEquals(1_300_000L, balancesBefore(t3).values.sum())
        assertEquals(1_300_000L, balancesBefore(t4).values.sum()) // setelah transfer 300.000
        assertEquals(1_250_000L, balancesBefore(t5).values.sum())
    }

    @Test
    fun `dompet yang dibuka setelah waktu tertentu belum dihitung saldo awalnya`() = runBlocking {
        val late = db.walletDao().insert(wallet("Dompet Baru", 700_000).copy(openedAt = t4))
        assertEquals(0L, balancesBefore(t3)[late])
        assertEquals(0L, balancesBefore(t4)[late])     // openedAt < before bersifat eksklusif
        assertEquals(700_000L, balancesBefore(t5)[late])
    }

    @Test
    fun `saldo awal ditambah arus periode sama dengan saldo akhir`() = runBlocking {
        val dao = db.transactionDao()
        val opening = balancesBefore(t1)
        val closing = balancesBefore(t5 + 1)
        val txs = dao.getBetweenAscending(t1, t5)
        listOf(walletA, walletB).forEach { id ->
            val effect = txs.sumOf { t ->
                when {
                    t.type == TransactionType.INCOME && t.walletId == id -> t.amount
                    t.type == TransactionType.EXPENSE && t.walletId == id -> -t.amount
                    t.type == TransactionType.TRANSFER && t.walletId == id -> -t.amount
                    t.type == TransactionType.TRANSFER && t.toWalletId == id -> t.amount
                    else -> 0L
                }
            }
            assertEquals(closing[id], opening.getValue(id) + effect)
        }
    }
    @Test
    fun `total per kategori dan per tipe`() = runBlocking {
        val dao = db.transactionDao()
        val byCategory = dao.getCategoryTotals(TransactionType.EXPENSE, t1, t5)
        assertEquals(1, byCategory.size)
        assertEquals(food, byCategory.first().categoryId)
        assertEquals(250_000L, byCategory.first().total)
        assertEquals(2, byCategory.first().txCount)

        val types = dao.getTypeTotals(t1, t5).associate { it.type to it.total }
        assertEquals(500_000L, types[TransactionType.INCOME])
        assertEquals(250_000L, types[TransactionType.EXPENSE])
        assertEquals(300_000L, types[TransactionType.TRANSFER])
    }

    @Test
    fun `total per hari dikelompokkan sesuai offset zona waktu`() = runBlocking {
        val days = db.transactionDao().getDayTotals(t1, t5, 0)
        assertTrue(days.isNotEmpty())
        assertEquals(750_000L, days.sumOf { it.total }) // pemasukan 500 + pengeluaran 250 (transfer tidak ikut)
    }

    @Test
    fun `dompet yang punya transaksi tidak bisa dihapus karena RESTRICT`() {
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { db.walletDao().delete(db.walletDao().getById(walletA)!!) }
        }
    }

    @Test
    fun `hapus kategori mengosongkan kategori transaksi bukan menghapus transaksi`() = runBlocking {
        db.categoryDao().delete(db.categoryDao().getById(food)!!)
        assertEquals(4, db.transactionDao().count())
        assertTrue(db.transactionDao().getAllAscending().all { it.categoryId == null })
    }

    @Test
    fun `tag tersimpan dan terbaca kembali`() = runBlocking {
        val tags = db.transactionDao().getAllAscending().first { it.note == "Kopi enak" }.tags
        assertEquals(listOf("kantor"), tags)
        assertEquals(listOf("|kantor|"), db.transactionDao().getDistinctTagStrings())
    }

    private suspend fun search(filter: TransactionFilter) =
        db.transactionDao().getFiltered(TransactionFilterQuery.toSupportQuery(filter))

    @Test
    fun `filter dinamis dengan kata kunci catatan kategori dompet tag dan nominal`() = runBlocking {
        assertEquals(1, search(TransactionFilter(query = "kopi")).size)
        assertEquals(2, search(TransactionFilter(query = "makanan")).size)      // nama kategori
        assertEquals(1, search(TransactionFilter(query = "Dompet B")).size.coerceAtMost(1)) // nama dompet
        assertEquals(1, search(TransactionFilter(tags = setOf("kantor"))).size)
        assertEquals(1, search(TransactionFilter(query = "50.000")).size)       // nominal persis
        assertEquals(0, search(TransactionFilter(query = "tidak-ada")).size)
        assertEquals(0, search(TransactionFilter(tags = setOf("kan"))).size)    // tag parsial tidak cocok
    }

    @Test
    fun `filter dinamis dengan tipe rentang nominal rentang waktu dan dompet`() = runBlocking {
        assertEquals(2, search(TransactionFilter(types = setOf(TransactionType.EXPENSE))).size)
        assertEquals(3, search(TransactionFilter(minAmount = 200_000)).size) // 500rb, 200rb, 300rb
        assertEquals(1, search(TransactionFilter(minAmount = 100_000, maxAmount = 250_000)).size)
        assertEquals(2, search(TransactionFilter(fromMillis = t1, toMillis = t2)).size)
        // Transfer A->B muncul saat memfilter dompet B (sebagai tujuan).
        val forB = search(TransactionFilter(walletIds = setOf(walletB)))
        assertEquals(2, forB.size)
    }

    @Test
    fun `hasil filter berurutan terbaru dulu dan injeksi SQL tidak berpengaruh`() = runBlocking {
        val all = search(TransactionFilter())
        assertEquals(listOf(t4, t3, t2, t1), all.map { it.occurredAt })
        assertEquals(0, search(TransactionFilter(query = "'; DROP TABLE transactions; --")).size)
        assertEquals(4, db.transactionDao().count())
    }

    @Test
    fun `arsip bulanan menjaga nomor dokumen unik revisi unik dan menyembunyikan SUPERSEDED`() = runBlocking {
        val dao = db.statementDao()
        fun statement(revision: Int, doc: String, status: StatementStatus) = MonthlyStatementEntity(
            periodYm = 202611, startDate = t1, endDate = t5, openingBalance = 0, totalIncome = 0, totalExpense = 0,
            totalTransfer = 0, closingBalance = 0, netChange = 0, transactionCount = 0, status = status,
            revision = revision, documentNumber = doc, snapshotJson = "{}", checksum = "x", createdAt = t1,
        )
        dao.insert(statement(1, "DKU-202611-0001", StatementStatus.SUPERSEDED))
        dao.insert(statement(2, "DKU-202611-0002", StatementStatus.CLOSED))

        val list = dao.getLatestSummaries()
        assertEquals(1, list.size)
        assertEquals(2, list.first().revision)
        assertEquals(listOf(202611), dao.getClosedPeriods())
        assertEquals(2, dao.maxRevision(202611))

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { dao.insert(statement(3, "DKU-202611-0002", StatementStatus.CLOSED)) }
        }
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { dao.insert(statement(2, "DKU-202611-0009", StatementStatus.CLOSED)) }
        }
        assertFalse(dao.getActive(202611)?.status == StatementStatus.SUPERSEDED)
    }
}
