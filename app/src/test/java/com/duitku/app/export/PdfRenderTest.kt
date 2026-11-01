package com.duitku.app.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.duitku.app.core.util.toKey
import com.duitku.app.data.export.ExportRepositoryImpl
import com.duitku.app.data.local.DuitKuDatabase
import com.duitku.app.data.repository.BudgetRepositoryImpl
import com.duitku.app.data.repository.StatementRepositoryImpl
import com.duitku.app.data.seed.DemoDataSeeder
import com.duitku.app.domain.model.AppSettings
import com.duitku.app.domain.model.CloseResult
import com.duitku.app.domain.model.ExportFormat
import com.duitku.app.domain.model.ExportKind
import com.duitku.app.domain.model.ExportOptions
import com.duitku.app.domain.model.PdfTheme
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.domain.usecase.category.SeedDefaultCategoriesUseCase
import com.duitku.app.domain.usecase.report.AnnualInput
import com.duitku.app.domain.usecase.report.AnnualReportBuilder
import com.duitku.app.domain.usecase.report.DocumentInfo
import com.duitku.app.domain.usecase.report.ReportBuilder
import com.duitku.app.domain.usecase.statement.Checksum
import com.duitku.app.domain.usecase.statement.SnapshotFactory
import com.duitku.app.export.pdf.AnnualPdfRenderer
import com.duitku.app.export.pdf.PdfSink
import com.duitku.app.export.pdf.PdfWriter
import com.duitku.app.export.pdf.QrDrawer
import com.duitku.app.export.pdf.ReportPdfRenderer
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Menulis tiap halaman ke PNG agar tata letak PDF bisa diperiksa secara visual. */
private class BitmapSink(private val dir: File, private val prefix: String) : PdfSink {
    var pages = 0
    private var bitmap: Bitmap? = null
    private val scale = 1.5f

    override fun startPage(pageNumber: Int, width: Int, height: Int): Canvas {
        val bmp = Bitmap.createBitmap((width * scale).toInt(), (height * scale).toInt(), Bitmap.Config.ARGB_8888)
        bitmap = bmp
        return Canvas(bmp).apply {
            drawColor(Color.WHITE)
            scale(scale, scale)
        }
    }

    override fun finishPage(pageNumber: Int) {
        pages = pageNumber
        dir.mkdirs()
        File(dir, "$prefix-${"%02d".format(pageNumber)}.png").outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }
}

/** PdfDocument butuh pustaka native Android yang tidak ada di JVM; halaman tetap dirender penuh ke bitmap. */
private class FakePdfWriter : PdfWriter {
    override fun write(render: (PdfSink) -> Int): ByteArray {
        val sink = BitmapSink(File("build/pdf-preview"), "export")
        render(sink)
        return "%PDF-fake pages=${sink.pages}".toByteArray()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfRenderTest {

    private lateinit var db: DuitKuDatabase
    private lateinit var statements: StatementRepositoryImpl
    private val outDir = File("build/pdf-preview")

    private val settings = object : SettingsRepository {
        private val s = MutableStateFlow(AppSettings(onboardingDone = true, ownerName = "Dewi Lestari"))
        override val settings: Flow<AppSettings> = s
        override suspend fun current() = s.value
        override suspend fun update(transform: (AppSettings) -> AppSettings) { s.value = transform(s.value) }
    }

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DuitKuDatabase::class.java)
            .allowMainThreadQueries().build()
        val budgets = BudgetRepositoryImpl(db.budgetDao(), db.transactionDao(), settings)
        statements = StatementRepositoryImpl(
            db, db.statementDao(), db.transactionDao(), db.walletDao(), db.categoryDao(), db.auditLogDao(),
            db.savingGoalDao(), db.debtDao(), db.recurringDao(), budgets, settings,
        )
        val categories = com.duitku.app.data.repository.CategoryRepositoryImpl(db.categoryDao())
        DemoDataSeeder(
            db, db.walletDao(), db.categoryDao(), db.transactionDao(), db.budgetDao(), db.savingGoalDao(), db.debtDao(), db.recurringDao(),
            SeedDefaultCategoriesUseCase(categories), settings,
        ).seed()
        Unit
    }

    @After
    fun tearDown() = db.close()

    private fun lastCompletePeriod(): Int = YearMonth.from(LocalDate.now()).minusMonths(1).toKey()

    private suspend fun finalReport(options: ExportOptions): com.duitku.app.domain.model.ReportModel {
        val ym = options.fromPeriodYm
        val closed = statements.close(ym) as CloseResult.Closed
        val snapshot = statements.getSnapshot(closed.summary.id)!!
        val doc = DocumentInfo(closed.summary.documentNumber, true, closed.summary.revisionLabel, closed.summary.checksum, "Bulan ini ada bonus proyek dan servis kendaraan.", System.currentTimeMillis())
        return ReportBuilder.build(snapshot, options, doc)
    }

    @Test
    fun `PDF bulanan tema klasik dengan grafik terrender lengkap`() = runBlocking {
        val ym = lastCompletePeriod()
        val options = ExportOptions(fromPeriodYm = ym, toPeriodYm = ym, theme = PdfTheme.CLASSIC, includeCharts = true, bankBalances = mapOf(1L to 1_000_000L))
        val model = finalReport(options)
        val sink = BitmapSink(outDir, "classic")
        val pages = ReportPdfRenderer(model, options).render(sink)
        assertEquals(pages, sink.pages)
        assertTrue("laporan 1 bulan data contoh seharusnya > 4 halaman, dapat $pages", pages >= 5)
        assertTrue(model.reconciliation.isBalanced)
        assertTrue(model.days.isNotEmpty())
    }

    @Test
    fun `PDF tema modern satu dompet tanpa grafik lebih pendek`() = runBlocking {
        val ym = lastCompletePeriod()
        val wallet = db.walletDao().getAll().first { it.name.startsWith("Bank") }.id
        val withCharts = ExportOptions(fromPeriodYm = ym, toPeriodYm = ym, walletId = wallet, theme = PdfTheme.MODERN, includeCharts = true)
        val model = finalReport(withCharts)
        val pagesWith = ReportPdfRenderer(model, withCharts).render(BitmapSink(outDir, "modern-bank"))
        val pagesWithout = ReportPdfRenderer(model, withCharts.copy(includeCharts = false)).render(BitmapSink(outDir, "modern-nochart"))
        assertTrue("tanpa grafik harus lebih pendek ($pagesWithout vs $pagesWith)", pagesWithout < pagesWith)
        assertEquals("Bank (Contoh)", model.meta.walletScope)
        assertTrue(model.reconciliation.isBalanced)
    }

    @Test
    fun `PDF hemat tinta tidak memakai warna selain abu-abu`() = runBlocking {
        val ym = lastCompletePeriod()
        val options = ExportOptions(fromPeriodYm = ym, toPeriodYm = ym, theme = PdfTheme.ECO, includeCharts = true)
        val model = finalReport(options)
        val sink = BitmapSink(outDir, "eco")
        ReportPdfRenderer(model, options).render(sink)
        val bmp = android.graphics.BitmapFactory.decodeFile(File(outDir, "eco-01.png").path)
        var colored = 0
        for (x in 0 until bmp.width step 3) for (y in 0 until bmp.height step 3) {
            val p = bmp.getPixel(x, y)
            if (Color.red(p) != Color.green(p) || Color.green(p) != Color.blue(p)) colored++
        }
        assertEquals("tema hemat tinta harus monokrom", 0, colored)
    }

    @Test
    fun `laporan tahunan 12 bulan terrender`() = runBlocking {
        val last = YearMonth.from(LocalDate.now()).minusMonths(1)
        val periods = (11 downTo 0).map { last.minusMonths(it.toLong()).toKey() }
        val inputs = periods.map { ym ->
            val snapshot = statements.buildLiveSnapshot(ym)
            AnnualInput(snapshot, false, Checksum.sha256Hex(SnapshotFactory.encode(snapshot)))
        }
        val annual = AnnualReportBuilder.build(inputs, "LAPORAN KEUANGAN TAHUNAN", "Dewi Lestari", System.currentTimeMillis(), "IDR")
        val sink = BitmapSink(outDir, "annual")
        val pages = AnnualPdfRenderer(annual, PdfTheme.CLASSIC, true).render(sink)
        assertTrue(pages >= 3)
        assertEquals(12, annual.months.size)
        assertTrue(annual.mostExpensiveMonth != null && annual.thriftiestMonth != null)
        assertTrue(annual.mostExpensiveMonth!!.expense >= annual.thriftiestMonth!!.expense)
        assertEquals(annual.totalIncome - annual.totalExpense, annual.totalNet)
    }

    @Test
    fun `ekspor menyeluruh membuat PDF CSV dan XLSX serta riwayat`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val export = ExportRepositoryImpl(context, statements, settings, db.exportHistoryDao(), FakePdfWriter(), Dispatchers.IO)
        val ym = lastCompletePeriod()
        val progress = mutableListOf<Int>()

        val pdf = export.export(ExportOptions(ExportFormat.PDF, ExportKind.MONTHLY, ym, ym)) { progress += it.percent }
        assertTrue(pdf.sizeBytes > 0)
        assertEquals(100, progress.last())
        assertTrue("kemajuan harus naik monoton: $progress", progress.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(pdf.fileName.endsWith("_DRAFT.pdf") && !pdf.isFinal) // bulan belum ditutup -> Draft

        val csv = export.export(ExportOptions(ExportFormat.CSV, ExportKind.MONTHLY, ym, ym))
        val csvText = ExportRepositoryImpl.fileOf(context, csv.fileName).readText()
        assertTrue(csvText.startsWith("\uFEFFPeriode,No,ID,Tanggal"))
        assertTrue(csvText.lines().size > 20)

        val xlsx = export.export(ExportOptions(ExportFormat.XLSX, ExportKind.MONTHLY, ym, ym))
        val entries = mutableListOf<String>()
        ZipInputStream(ExportRepositoryImpl.fileOf(context, xlsx.fileName).inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entries += it.name }
        }
        assertTrue(entries.containsAll(listOf("[Content_Types].xml", "xl/workbook.xml", "xl/worksheets/sheet1.xml", "xl/styles.xml")))

        val annual = export.export(ExportOptions(ExportFormat.PDF, ExportKind.ANNUAL, YearMonth.of(2026, 1).toKey(), YearMonth.of(2026, 10).toKey()))
        assertTrue(annual.sizeBytes > 0)
        assertEquals(4, db.exportHistoryDao().getAll().size)

        export.deleteRecord(csv.historyId)
        assertTrue(!ExportRepositoryImpl.fileOf(context, csv.fileName).exists())
        assertEquals(3, db.exportHistoryDao().getAll().size)
    }

    @Test
    fun `kode QR dapat dibuat untuk nomor dokumen dan hash`() {
        val payload = ReportBuilder.qrPayload("DKU-202610-0001", Checksum.sha256Hex("x"))
        assertTrue(QrDrawer.modules(payload) >= 29)
        assertTrue(payload.startsWith("DUITKU|DKU-202610-0001|"))
    }
}
