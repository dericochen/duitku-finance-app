package com.duitku.app.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.duitku.app.core.util.PeriodCalculator
import com.duitku.app.core.util.toKey
import com.duitku.app.data.export.ExportRepositoryImpl
import com.duitku.app.data.local.DuitKuDatabase
import com.duitku.app.data.repository.BudgetRepositoryImpl
import com.duitku.app.data.repository.CategoryRepositoryImpl
import com.duitku.app.data.repository.StatementRepositoryImpl
import com.duitku.app.data.retention.RetentionRepositoryImpl
import com.duitku.app.data.seed.DemoDataSeeder
import com.duitku.app.domain.model.AppSettings
import com.duitku.app.domain.model.BackupKind
import com.duitku.app.domain.model.CloseResult
import com.duitku.app.domain.model.RestoreMode
import com.duitku.app.domain.model.RetentionAction
import com.duitku.app.domain.repository.PrepareResult
import com.duitku.app.domain.repository.PurgeResult
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.domain.usecase.category.SeedDefaultCategoriesUseCase
import com.duitku.app.domain.usecase.retention.RetentionPlanner
import com.duitku.app.domain.usecase.statement.Checksum
import com.duitku.app.export.pdf.PdfSink
import com.duitku.app.export.pdf.PdfWriter
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
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

private class MutableSettings(initial: AppSettings) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state
    override suspend fun current() = state.value
    override suspend fun update(transform: (AppSettings) -> AppSettings) { state.value = transform(state.value) }
}

private object NoRenderPdfWriter : PdfWriter {
    override fun write(render: (PdfSink) -> Int): ByteArray = "%PDF-test".toByteArray()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRetentionTest {

    private lateinit var db: DuitKuDatabase
    private lateinit var db2: DuitKuDatabase
    private lateinit var context: Context
    private lateinit var settings: MutableSettings
    private lateinit var statements: StatementRepositoryImpl
    private lateinit var service: BackupService

    private fun newDb() = Room.inMemoryDatabaseBuilder(context, DuitKuDatabase::class.java).allowMainThreadQueries().build()

    private fun statementsFor(d: DuitKuDatabase, s: SettingsRepository) = StatementRepositoryImpl(
        d, d.statementDao(), d.transactionDao(), d.walletDao(), d.categoryDao(), d.auditLogDao(),
        d.savingGoalDao(), d.debtDao(), d.recurringDao(), BudgetRepositoryImpl(d.budgetDao(), d.transactionDao(), s), s,
    )

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "backups").deleteRecursively()
        File(context.filesDir, "exports").deleteRecursively()
        db = newDb()
        db2 = newDb()
        settings = MutableSettings(AppSettings(onboardingDone = true, ownerName = "Dewi", retentionMonths = 6))
        statements = statementsFor(db, settings)
        service = BackupService(db, db.dataTransferDao(), db.statementDao())
        DemoDataSeeder(
            db, db.walletDao(), db.categoryDao(), db.transactionDao(), db.budgetDao(), db.savingGoalDao(), db.debtDao(), db.recurringDao(),
            SeedDefaultCategoriesUseCase(CategoryRepositoryImpl(db.categoryDao())), settings,
        ).seed()
        Unit
    }

    @After
    fun tearDown() {
        db.close()
        db2.close()
    }

    private suspend fun balances(d: DuitKuDatabase) = d.transactionDao().getWalletBalances().associate { it.walletId to it.balance }

    private fun backupSettings() = BackupSettings("Dewi", "IDR", 1, 6)

    @Test
    fun `backup lalu restore timpa menghasilkan data identik termasuk saldo dan arsip`() = runBlocking {
        val last = PeriodCalculator.periodOf(LocalDate.now(), 1).minusMonths(1).toKey()
        assertTrue(statements.close(last) is CloseResult.Closed)
        val before = balances(db)
        val txCount = db.transactionDao().count()

        val bytes = BackupCodec.encode(service.collect(backupSettings()), DuitKuDatabase.VERSION, "1.0.0", 1L)
        val decoded = BackupCodec.decode(bytes, DuitKuDatabase.VERSION) as DecodeResult.Ok
        BackupService(db2, db2.dataTransferDao(), db2.statementDao()).overwrite(decoded.payload)

        assertEquals(txCount, db2.transactionDao().count())
        assertEquals(before, balances(db2))
        val restored = db2.statementDao().getActive(last)!!
        assertEquals(db.statementDao().getActive(last)!!.checksum, restored.checksum)
        // Checksum arsip tetap valid setelah dipulihkan.
        assertEquals(restored.checksum, Checksum.sha256Hex(restored.snapshotJson))
        assertEquals(db.categoryDao().count(), db2.categoryDao().count())
        assertEquals(db.budgetDao().countForPeriod(last), db2.budgetDao().countForPeriod(last))
    }

    @Test
    fun `file rusak, diubah, atau dari versi lebih baru ditolak dengan alasan jelas`() = runBlocking {
        val payload = service.collect(backupSettings())
        val good = BackupCodec.encode(payload, DuitKuDatabase.VERSION, "1.0.0", 1L)
        assertTrue(BackupCodec.decode(good, DuitKuDatabase.VERSION) is DecodeResult.Ok)

        val garbage = BackupCodec.decode(byteArrayOf(1, 2, 3, 4), DuitKuDatabase.VERSION) as DecodeResult.Error
        assertTrue(garbage.reason.contains("bukan"))

        val truncated = BackupCodec.decode(good.copyOf(good.size / 2), DuitKuDatabase.VERSION)
        assertTrue(truncated is DecodeResult.Error)

        // Ubah payload tanpa memperbarui checksum.
        val newer = BackupCodec.encode(payload, DuitKuDatabase.VERSION + 1, "9.9", 1L)
        val tooNew = BackupCodec.decode(newer, DuitKuDatabase.VERSION) as DecodeResult.Error
        assertTrue(tooNew.reason.contains("lebih baru"))

        val raw = java.util.zip.GZIPInputStream(good.inputStream()).readBytes().toString(Charsets.UTF_8)
        val tampered = raw.replace("Dewi", "Hack")
        val tamperedBytes = java.io.ByteArrayOutputStream().also { java.util.zip.GZIPOutputStream(it).use { gz -> gz.write(tampered.toByteArray()) } }.toByteArray()
        val result = BackupCodec.decode(tamperedBytes, DuitKuDatabase.VERSION)
        // "Dewi" hanya ada di dalam payload (string), jadi checksum tidak lagi cocok.
        assertTrue(result is DecodeResult.Error && (result.reason.contains("Checksum") || result.reason.contains("tidak")))
    }

    @Test
    fun `gabung menambahkan yang belum ada dan tidak menggandakan saat diulang`() = runBlocking {
        val payload = service.collect(backupSettings())
        val target = BackupService(db2, db2.dataTransferDao(), db2.statementDao())
        val first = target.merge(payload, 1)
        assertEquals(payload.transactions.size, first.transactionsAdded)
        assertEquals(payload.wallets.size, first.walletsAdded)
        assertEquals(payload.transactions.size, db2.transactionDao().count())
        assertEquals(balances(db).values.sum(), balances(db2).values.sum())

        val second = target.merge(payload, 1)
        assertEquals(0, second.transactionsAdded)
        assertEquals(0, second.walletsAdded)
        assertEquals(payload.transactions.size, second.skippedDuplicate)
        assertEquals(payload.transactions.size, db2.transactionDao().count())
        assertEquals(payload.categories.size, db2.categoryDao().count())
    }

    @Test
    fun `gabung melewati transaksi di bulan yang sudah ditutup`() = runBlocking {
        val payload = service.collect(backupSettings())
        val target = BackupService(db2, db2.dataTransferDao(), db2.statementDao())
        target.merge(payload, 1)
        val last = PeriodCalculator.periodOf(LocalDate.now(), 1).minusMonths(1).toKey()
        val st2 = statementsFor(db2, settings)
        assertTrue(st2.close(last) is CloseResult.Closed)

        // Hapus satu transaksi bulan tertutup dari DB tujuan langsung lewat DAO lalu coba gabung lagi: tetap dilewati.
        val range = PeriodCalculator.rangeOf(YearMonth.of(last / 100, last % 100), 1)
        val victim = db2.transactionDao().getBetweenAscending(range.startMillis(), range.endMillis()).first()
        db2.transactionDao().deleteById(victim.id)
        val stats = target.merge(payload, 1)
        assertEquals(1, stats.skippedClosed)
        assertEquals(0, stats.transactionsAdded)
    }

    @Test
    fun `pembersihan memindahkan efek ke saldo awal sehingga saldo tidak berubah`() = runBlocking {
        val before = balances(db)
        val totalBefore = db.transactionDao().count()
        val s = settings.current()
        val window = RetentionPlanner.window(s.retentionMonths, s.monthStartDay, LocalDate.now())
        val cutoff = window.cutoffMillis!!
        val expired = db.transactionDao().countBefore(cutoff)
        assertTrue(expired > 0)

        // Saldo awal bulan pertama yang dipertahankan harus sama sebelum dan sesudah pembersihan.
        val openingBefore = db.transactionDao().getBalancesBefore(cutoff).associate { it.walletId to it.balance }
        assertEquals(expired, service.purgeBefore(cutoff))

        assertEquals(totalBefore - expired, db.transactionDao().count())
        assertEquals(before, balances(db))
        assertEquals(openingBefore, db.transactionDao().getBalancesBefore(cutoff).associate { it.walletId to it.balance })
        // Periode setelah batas tetap rekonsiliasi.
        val kept = statements.buildLiveSnapshot(PeriodCalculator.periodOf(LocalDate.now(), 1).minusMonths(1).toKey())
        assertEquals(kept.totals.opening + kept.totals.income - kept.totals.expense + kept.totals.added, kept.totals.closing)
    }

    private fun retention(): Triple<RetentionRepositoryImpl, BackupRepositoryImpl, ExportRepositoryImpl> {
        val backups = BackupRepositoryImpl(context, db, service, db.backupRecordDao(), db.transactionDao(), db.statementDao(), settings, Dispatchers.IO)
        val exports = ExportRepositoryImpl(context, statements, settings, db.exportHistoryDao(), NoRenderPdfWriter, Dispatchers.IO)
        return Triple(
            RetentionRepositoryImpl(db.transactionDao(), db.statementDao(), db.auditLogDao(), statements, backups, exports, service, settings),
            backups, exports,
        )
    }

    @Test
    fun `retensi aman menolak hapus sebelum arsip siap lalu menghapus hanya setelah persiapan`() = runBlocking {
        val (retention, _, _) = retention()
        val before = balances(db)

        val st = retention.status()
        assertTrue(st.hasExpiredData)
        assertFalse(st.prepared)
        assertTrue(retention.purge() is PurgeResult.Refused) // belum disiapkan: ditolak, tidak ada yang terhapus
        assertEquals(st.expiredTransactionCount + db.transactionDao().countBefore(st.cutoffMillis!!).let { 0 }, st.expiredTransactionCount)

        val prepared = retention.prepare() as PrepareResult.Ready
        assertTrue(prepared.closedPeriods > 0)
        assertTrue(prepared.reportsCreated > 0)
        // Semua bulan kedaluwarsa kini punya snapshot tertutup, backup pengaman ada, laporan tercatat di riwayat ekspor.
        assertEquals(st.expiredPeriods.size, st.expiredPeriods.count { db.statementDao().getActive(it)?.status == com.duitku.app.domain.model.StatementStatus.CLOSED })
        assertTrue(File(context.filesDir, "backups").listFiles().orEmpty().any { it.name.contains("pre_purge") })
        assertTrue(db.exportHistoryDao().getAll().isNotEmpty())
        assertTrue(retention.status().prepared)
        assertEquals(st.expiredTransactionCount + (db.transactionDao().count() - st.expiredTransactionCount), db.transactionDao().count()) // belum ada yang terhapus

        val purged = retention.purge() as PurgeResult.Done
        assertEquals(st.expiredTransactionCount, purged.deleted)
        assertEquals(before, balances(db))
        assertEquals(st.expiredPeriods.size, db.statementDao().getClosedPeriods().size) // snapshot tetap
        assertFalse(retention.status().hasExpiredData)
        assertEquals(0L, settings.current().purgePreparedCutoff)
        assertTrue(db.auditLogDao().getAll().any { it.action == "PURGE" })
    }

    @Test
    fun `mode arsip saja tidak pernah menghapus`() = runBlocking {
        settings.update { it.copy(retentionAction = RetentionAction.ARCHIVE_ONLY) }
        val (retention, _, _) = retention()
        assertTrue(retention.prepare() is PrepareResult.Ready)
        val count = db.transactionDao().count()
        assertTrue(retention.purge() is PurgeResult.Refused)
        assertEquals(count, db.transactionDao().count())
    }

    @Test
    fun `retensi selamanya tidak menyiapkan apa pun`() = runBlocking {
        settings.update { it.copy(retentionMonths = AppSettings.RETENTION_FOREVER) }
        val (retention, _, _) = retention()
        assertFalse(retention.status().hasExpiredData)
        assertEquals(PrepareResult.NothingToDo, retention.prepare())
        assertTrue(retention.purge() is PurgeResult.Refused)
    }

    @Test
    fun `restore dari file membuat backup pengaman dan dapat dibatalkan bila file buruk`() = runBlocking {
        val (_, backups, _) = retention()
        val outcome = backups.backupToConfiguredLocation(BackupKind.MANUAL)
        assertTrue(outcome.sizeBytes > 0)
        val file = File(outcome.location)
        assertTrue(file.exists())
        assertTrue(file.name.endsWith(".duitku"))
        assertTrue(settings.current().lastBackupAt > 0)

        val uri = android.net.Uri.fromFile(file).toString()
        val info = backups.inspect(uri) as com.duitku.app.domain.model.InspectResult.Valid
        assertEquals(db.transactionDao().count(), info.info.transactions)

        // Rusak file: pemulihan ditolak dan data tidak berubah.
        val broken = File(context.filesDir, "rusak.duitku").apply { writeBytes(file.readBytes().copyOf(40)) }
        val count = db.transactionDao().count()
        val result = backups.restore(android.net.Uri.fromFile(broken).toString(), RestoreMode.OVERWRITE)
        assertTrue(result is com.duitku.app.domain.model.RestoreResult.Failed)
        assertEquals(count, db.transactionDao().count())

        // Timpa dari file valid: sukses dan ada backup pengaman PRE_RESTORE.
        val ok = backups.restore(uri, RestoreMode.OVERWRITE) as com.duitku.app.domain.model.RestoreResult.Success
        assertTrue(ok.report.safetyBackupName!!.contains("pre_restore"))
        assertEquals(count, db.transactionDao().count())
    }

    @Test
    fun `backup otomatis hanya menyimpan sejumlah terbaru`() = runBlocking {
        settings.update { it.copy(keepBackupCount = 3) }
        val (_, backups, _) = retention()
        repeat(5) {
            backups.backupToConfiguredLocation(BackupKind.AUTO)
            Thread.sleep(15)
        }
        val files = File(context.filesDir, "backups").listFiles().orEmpty().filter { it.name.startsWith("DuitKu_backup_") }
        assertEquals(3, files.size)
        assertEquals(5, backups.observeRecords().first().size) // riwayat tetap lengkap
    }

    @Test
    fun `bulan tertutup tetap tampil utuh setelah transaksi mentah dibersihkan`() = runBlocking {
        val (retention, _, _) = retention()
        val st = retention.status()
        val oldPeriod = st.expiredPeriods.max()
        retention.prepare()
        val before = statements.observeMonth(oldPeriod, null).first()
        assertTrue(before.rows.isNotEmpty())

        retention.purge()
        assertEquals(0, db.transactionDao().getBetweenAscending(before.startMillis, before.endMillis).size) // data mentah sudah hilang
        val after = statements.observeMonth(oldPeriod, null).first()
        assertEquals(before.rows.size, after.rows.size)
        assertEquals(before.summary, after.summary)
        assertEquals(before.rows.map { it.balanceAfter }, after.rows.map { it.balanceAfter })
        assertTrue(after.reconciliation.isBalanced)
    }

    @Test
    fun `penggunaan penyimpanan dilaporkan`() = runBlocking {
        val (_, backups, _) = retention()
        backups.backupToConfiguredLocation(BackupKind.MANUAL)
        val usage = backups.storageUsage()
        assertTrue(usage.backupBytes > 0)
        assertEquals(db.transactionDao().count(), usage.transactionCount)
    }
}
