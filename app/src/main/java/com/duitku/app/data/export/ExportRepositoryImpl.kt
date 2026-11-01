package com.duitku.app.data.export

import android.content.Context
import android.net.Uri
import com.duitku.app.core.util.DateFormats
import com.duitku.app.core.util.PeriodCalculator
import com.duitku.app.core.util.toKey
import com.duitku.app.core.util.toYearMonth
import com.duitku.app.data.local.dao.ExportHistoryDao
import com.duitku.app.data.local.entity.ExportHistoryEntity
import com.duitku.app.di.IoDispatcher
import com.duitku.app.domain.model.ExportFormat
import com.duitku.app.domain.model.ExportKind
import com.duitku.app.domain.model.ExportOptions
import com.duitku.app.domain.model.ExportProgress
import com.duitku.app.domain.model.ExportResult
import com.duitku.app.domain.model.ReportModel
import com.duitku.app.domain.model.StatementSnapshot
import com.duitku.app.domain.repository.ExportRecord
import com.duitku.app.domain.repository.ExportRepository
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.domain.repository.StatementRepository
import com.duitku.app.domain.usecase.report.AnnualInput
import com.duitku.app.domain.usecase.report.AnnualReportBuilder
import com.duitku.app.domain.usecase.report.DocumentInfo
import com.duitku.app.domain.usecase.report.ReportBuilder
import com.duitku.app.domain.usecase.statement.Checksum
import com.duitku.app.domain.usecase.statement.SnapshotFactory
import com.duitku.app.export.CsvWriter
import com.duitku.app.export.XlsxWriter
import com.duitku.app.export.pdf.AnnualPdfRenderer
import com.duitku.app.export.pdf.PdfSink
import com.duitku.app.export.pdf.PdfWriter
import com.duitku.app.export.pdf.ReportPdfRenderer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Membuat berkas ekspor. Bulan tertutup dibaca dari arsip (snapshot + checksum, status Final);
 * bulan berjalan dibangun dari data saat ini dan ditandai Draft.
 */
@Singleton
class ExportRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val statements: StatementRepository,
    private val settingsRepository: SettingsRepository,
    private val historyDao: ExportHistoryDao,
    private val pdfWriter: PdfWriter,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ExportRepository {

    private val exportDir: File get() = File(context.filesDir, DIR).also { it.mkdirs() }

    override suspend fun export(options: ExportOptions, onProgress: (ExportProgress) -> Unit): ExportResult =
        withContext(io) {
            require(options.fromPeriodYm <= options.toPeriodYm) { "Periode awal setelah periode akhir" }
            onProgress(ExportProgress(5, "Menyiapkan data"))
            val settings = settingsRepository.current()
            val now = System.currentTimeMillis()
            val single = options.fromPeriodYm == options.toPeriodYm && options.kind == ExportKind.MONTHLY

            val periods = PeriodCalculator.monthsBetween(options.fromPeriodYm.toYearMonth(), options.toPeriodYm.toYearMonth()).map { it.toKey() }
            require(periods.size <= MAX_MONTHS) { "Rentang maksimal $MAX_MONTHS bulan" }

            val loaded = periods.mapIndexed { index, ym ->
                onProgress(ExportProgress(5 + 35 * (index + 1) / periods.size, "Membaca ${DateFormats.monthYear(ym.toYearMonth())}"))
                loadPeriod(ym, now)
            }

            val bytes: ByteArray
            val documentNumber: String?
            val isFinal: Boolean
            val baseName: String

            if (single) {
                val l = loaded.single()
                val model = ReportBuilder.build(l.snapshot, options, l.doc)
                documentNumber = model.meta.documentNumber
                isFinal = model.meta.isFinal
                baseName = "DuitKu_Mutasi_${options.fromPeriodYm}_${documentNumber}"
                onProgress(ExportProgress(55, "Menyusun ${options.format.name}"))
                bytes = when (options.format) {
                    ExportFormat.PDF -> renderPdf { ReportPdfRenderer(model, options).render(it) }
                    ExportFormat.CSV -> CsvWriter.monthly(listOf(model)).toByteArray(Charsets.UTF_8)
                    ExportFormat.XLSX -> xlsx(XlsxWriter.monthlySheets(model))
                }
            } else {
                val inputs = loaded.map { AnnualInput(it.snapshot, it.closed, it.doc.checksum) }
                val title = if (periods.size == 12 || options.kind == ExportKind.ANNUAL) "LAPORAN KEUANGAN TAHUNAN" else "LAPORAN MUTASI PERIODE"
                val annual = AnnualReportBuilder.build(inputs, title, settings.ownerName, now, settings.currencyCode, options.userNote.orEmpty())
                documentNumber = annual.meta.documentNumber
                isFinal = annual.meta.isFinal
                baseName = "DuitKu_${if (options.kind == ExportKind.ANNUAL) "Tahunan" else "Periode"}_${options.fromPeriodYm}-${options.toPeriodYm}"
                onProgress(ExportProgress(55, "Menyusun ${options.format.name}"))
                bytes = when (options.format) {
                    ExportFormat.PDF -> renderPdf { AnnualPdfRenderer(annual, options.theme, options.includeCharts).render(it) }
                    ExportFormat.CSV, ExportFormat.XLSX -> {
                        val monthModels: List<ReportModel> = loaded.map { ReportBuilder.build(it.snapshot, options.copy(walletId = null), it.doc) }
                        if (options.format == ExportFormat.CSV) {
                            CsvWriter.monthly(monthModels).toByteArray(Charsets.UTF_8)
                        } else {
                            xlsx(XlsxWriter.rangeSheets(annual, monthModels))
                        }
                    }
                }
            }

            onProgress(ExportProgress(90, "Menyimpan berkas"))
            val fileName = sanitize(baseName) + (if (isFinal) "" else "_DRAFT") + "." + options.format.extension
            File(exportDir, fileName).writeBytes(bytes)

            val id = historyDao.insert(
                ExportHistoryEntity(
                    fileName = fileName, location = fileName, format = options.format, kind = options.kind,
                    fromPeriodYm = options.fromPeriodYm, toPeriodYm = options.toPeriodYm, documentNumber = documentNumber,
                    checksum = Checksum.sha256Hex(bytes), sizeBytes = bytes.size.toLong(), createdAt = now,
                ),
            )
            onProgress(ExportProgress(100, "Selesai"))
            ExportResult(id, fileName, options.format, bytes.size.toLong(), documentNumber, isFinal)
        }

    private class Loaded(val snapshot: StatementSnapshot, val doc: DocumentInfo, val closed: Boolean)

    private suspend fun loadPeriod(periodYm: Int, now: Long): Loaded {
        val active = statements.getActive(periodYm)
        if (active != null && active.isClosed) {
            val snapshot = statements.getSnapshot(active.id) ?: error("Arsip $periodYm tidak terbaca")
            return Loaded(snapshot, DocumentInfo(active.documentNumber, true, active.revisionLabel, active.checksum, active.userNote, now), true)
        }
        val live = statements.buildLiveSnapshot(periodYm)
        val checksum = Checksum.sha256Hex(SnapshotFactory.encode(live))
        return Loaded(live, DocumentInfo("DRAFT-$periodYm", false, "Draft", checksum, active?.userNote.orEmpty(), now), false)
    }

    private fun renderPdf(render: (PdfSink) -> Int): ByteArray = pdfWriter.write(render)

    private fun xlsx(sheets: List<com.duitku.app.export.XlsxSheet>): ByteArray =
        java.io.ByteArrayOutputStream().also { XlsxWriter.write(sheets, it) }.toByteArray()

    private fun sanitize(name: String) = name.replace(Regex("[^A-Za-z0-9_.\\-]"), "_")

    override fun observeHistory(): Flow<List<ExportRecord>> =
        historyDao.observeAll().map { list -> list.map { it.toRecord() } }

    override suspend fun getRecord(id: Long): ExportRecord? = historyDao.getById(id)?.toRecord()

    override suspend fun deleteRecord(id: Long) = withContext(io) {
        historyDao.getById(id)?.let { File(exportDir, it.location).delete() }
        historyDao.deleteById(id)
    }

    override suspend fun copyTo(id: Long, destinationUri: String): Boolean = withContext(io) {
        val entity = historyDao.getById(id) ?: return@withContext false
        val source = File(exportDir, entity.location)
        if (!source.exists()) return@withContext false
        runCatching {
            context.contentResolver.openOutputStream(Uri.parse(destinationUri), "wt")?.use { out -> source.inputStream().use { it.copyTo(out) } } != null
        }.getOrDefault(false)
    }

    private fun ExportHistoryEntity.toRecord() = ExportRecord(
        id, fileName, format, kind, fromPeriodYm, toPeriodYm, documentNumber, sizeBytes, createdAt, File(exportDir, location).exists(),
    )

    companion object {
        const val DIR = "exports"
        const val MAX_MONTHS = 36

        fun fileOf(context: Context, fileName: String): File = File(File(context.filesDir, DIR), fileName)
    }
}
