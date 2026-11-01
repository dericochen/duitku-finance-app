package com.duitku.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.duitku.app.core.util.DateFormats
import com.duitku.app.data.export.ExportRepositoryImpl
import com.duitku.app.data.local.DuitKuDatabase
import com.duitku.app.data.local.dao.BackupRecordDao
import com.duitku.app.data.local.dao.StatementDao
import com.duitku.app.data.local.dao.TransactionDao
import com.duitku.app.data.local.entity.BackupRecordEntity
import com.duitku.app.di.IoDispatcher
import com.duitku.app.domain.model.BackupInfo
import com.duitku.app.domain.model.BackupKind
import com.duitku.app.domain.model.BackupOutcome
import com.duitku.app.domain.model.BackupRecordInfo
import com.duitku.app.domain.model.InspectResult
import com.duitku.app.domain.model.RestoreMode
import com.duitku.app.domain.model.RestoreReport
import com.duitku.app.domain.model.RestoreResult
import com.duitku.app.domain.model.StorageUsage
import com.duitku.app.domain.repository.BackupRepository
import com.duitku.app.domain.repository.SettingsRepository
import com.duitku.app.domain.usecase.statement.Checksum
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class BackupRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: DuitKuDatabase,
    private val service: BackupService,
    private val recordDao: BackupRecordDao,
    private val transactionDao: TransactionDao,
    private val statementDao: StatementDao,
    private val settingsRepository: SettingsRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : BackupRepository {

    private val internalDir: File get() = File(context.filesDir, DIR).also { it.mkdirs() }

    private suspend fun buildBytes(): Triple<ByteArray, Int, Long> {
        val settings = settingsRepository.current()
        val payload = service.collect(
            BackupSettings(settings.ownerName, settings.currencyCode, settings.monthStartDay, settings.retentionMonths),
        )
        val now = System.currentTimeMillis()
        val bytes = BackupCodec.encode(payload, DuitKuDatabase.VERSION, APP_VERSION, now)
        return Triple(bytes, payload.transactions.size, now)
    }

    private fun fileName(kind: BackupKind, now: Long): String {
        val prefix = if (kind == BackupKind.AUTO || kind == BackupKind.MANUAL) FILE_PREFIX else SAFETY_PREFIX
        // Milidetik ikut dicantumkan agar dua backup dalam detik yang sama tidak saling menimpa.
        return "${prefix}${DateFormats.fileStamp(now)}-${"%03d".format(now % 1000)}_${kind.name.lowercase()}.$EXTENSION"
    }

    override suspend fun backupToConfiguredLocation(kind: BackupKind): BackupOutcome = withContext(io) {
        val settings = settingsRepository.current()
        val (bytes, txCount, now) = buildBytes()
        val name = fileName(kind, now)
        val checksum = Checksum.sha256Hex(bytes)

        var location: String? = null
        var message = ""
        val useFolder = (kind == BackupKind.AUTO || kind == BackupKind.MANUAL) && settings.backupFolderUri.isNotBlank()
        if (useFolder) {
            location = runCatching { writeToFolder(settings.backupFolderUri, name, bytes, settings.keepBackupCount) }
                .onFailure { message = "Folder backup tidak dapat ditulis (${it.message}); disimpan di penyimpanan aplikasi" }
                .getOrNull()
        }
        if (location == null) {
            File(internalDir, name).writeBytes(bytes)
            pruneInternal(if (kind == BackupKind.AUTO || kind == BackupKind.MANUAL) FILE_PREFIX else SAFETY_PREFIX, if (kind == BackupKind.AUTO || kind == BackupKind.MANUAL) settings.keepBackupCount else SAFETY_KEEP)
            location = File(internalDir, name).path
        }
        recordDao.insert(
            BackupRecordEntity(
                fileName = name, location = location, kind = kind, dbVersion = DuitKuDatabase.VERSION, transactionCount = txCount,
                sizeBytes = bytes.size.toLong(), checksum = checksum, success = true, message = message, createdAt = now,
            ),
        )
        settingsRepository.update { it.copy(lastBackupAt = now) }
        BackupOutcome(name, location, bytes.size.toLong(), checksum, txCount)
    }

    override suspend fun backupToUri(uri: String, kind: BackupKind): BackupOutcome = withContext(io) {
        val (bytes, txCount, now) = buildBytes()
        val name = fileName(kind, now)
        val checksum = Checksum.sha256Hex(bytes)
        context.contentResolver.openOutputStream(Uri.parse(uri), "wt")?.use { it.write(bytes) }
            ?: error("Tidak dapat menulis ke lokasi yang dipilih")
        recordDao.insert(
            BackupRecordEntity(
                fileName = name, location = uri, kind = kind, dbVersion = DuitKuDatabase.VERSION, transactionCount = txCount,
                sizeBytes = bytes.size.toLong(), checksum = checksum, createdAt = now,
            ),
        )
        settingsRepository.update { it.copy(lastBackupAt = now) }
        BackupOutcome(name, uri, bytes.size.toLong(), checksum, txCount)
    }

    private fun writeToFolder(treeUri: String, name: String, bytes: ByteArray, keep: Int): String {
        val folder = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
        require(folder != null && folder.canWrite()) { "izin folder hilang" }
        val file = folder.createFile("application/octet-stream", name) ?: error("gagal membuat berkas")
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(bytes) } ?: error("gagal menulis berkas")
        // Simpan hanya [keep] backup terbaru di folder ini.
        folder.listFiles()
            .filter { it.name?.startsWith(FILE_PREFIX) == true && it.name?.endsWith(".$EXTENSION") == true }
            .sortedByDescending { it.lastModified() }
            .drop(keep.coerceAtLeast(1))
            .forEach { it.delete() }
        return file.uri.toString()
    }

    private fun pruneInternal(prefix: String, keep: Int) {
        internalDir.listFiles { f -> f.name.startsWith(prefix) }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(keep.coerceAtLeast(1))
            ?.forEach { it.delete() }
    }

    private fun read(uri: String): ByteArray {
        val stream = context.contentResolver.openInputStream(Uri.parse(uri)) ?: error("Berkas tidak dapat dibuka")
        return stream.use { input ->
            val bytes = input.readBytes()
            require(bytes.size <= MAX_BACKUP_BYTES) { "Berkas terlalu besar" }
            bytes
        }
    }

    override suspend fun inspect(uri: String): InspectResult = withContext(io) {
        val bytes = try { read(uri) } catch (e: Exception) { return@withContext InspectResult.Invalid(e.message ?: "Berkas tidak dapat dibaca") }
        when (val decoded = BackupCodec.decode(bytes, DuitKuDatabase.VERSION)) {
            is DecodeResult.Error -> InspectResult.Invalid(decoded.reason)
            is DecodeResult.Ok -> {
                val tx = decoded.payload.transactions
                InspectResult.Valid(
                    BackupInfo(
                        createdAt = decoded.envelope.createdAt, dbVersion = decoded.envelope.dbVersion, appVersion = decoded.envelope.appVersion,
                        wallets = decoded.payload.wallets.size, transactions = tx.size, statements = decoded.payload.statements.size,
                        oldestTransactionAt = tx.minOfOrNull { it.occurredAt }, newestTransactionAt = tx.maxOfOrNull { it.occurredAt },
                        sizeBytes = decoded.sizeBytes, checksum = decoded.envelope.checksum,
                    ),
                )
            }
        }
    }

    override suspend fun restore(uri: String, mode: RestoreMode): RestoreResult = withContext(io) {
        val bytes = try { read(uri) } catch (e: Exception) { return@withContext RestoreResult.Failed(e.message ?: "Berkas tidak dapat dibaca") }
        val decoded = when (val d = BackupCodec.decode(bytes, DuitKuDatabase.VERSION)) {
            is DecodeResult.Error -> return@withContext RestoreResult.Failed(d.reason)
            is DecodeResult.Ok -> d
        }
        // Pengaman: simpan kondisi saat ini sebelum mengubah apa pun. Bila gagal, pemulihan dibatalkan.
        val safety = try {
            backupToConfiguredLocation(BackupKind.PRE_RESTORE).fileName
        } catch (e: Exception) {
            return@withContext RestoreResult.Failed("Gagal membuat backup pengaman: ${e.message}")
        }
        try {
            val startDay = settingsRepository.current().monthStartDay
            when (mode) {
                RestoreMode.OVERWRITE -> {
                    service.overwrite(decoded.payload)
                    val s = decoded.payload.settings
                    settingsRepository.update {
                        it.copy(ownerName = s.ownerName, currencyCode = s.currencyCode, monthStartDay = s.monthStartDay, retentionMonths = s.retentionMonths, onboardingDone = true)
                    }
                    RestoreResult.Success(
                        RestoreReport(mode, decoded.payload.wallets.size, decoded.payload.transactions.size, 0, 0, decoded.payload.statements.size, safety),
                    )
                }
                RestoreMode.MERGE -> {
                    val stats = service.merge(decoded.payload, startDay)
                    settingsRepository.update { it.copy(onboardingDone = true) }
                    RestoreResult.Success(
                        RestoreReport(mode, stats.walletsAdded, stats.transactionsAdded, stats.skippedDuplicate, stats.skippedClosed, stats.statementsAdded, safety),
                    )
                }
            }
        } catch (e: Exception) {
            RestoreResult.Failed("Pemulihan gagal dan dibatalkan; data Anda tidak berubah. (${e.message})")
        }
    }

    override fun observeRecords(): Flow<List<BackupRecordInfo>> = recordDao.observeAll().map { list ->
        list.map { BackupRecordInfo(it.id, it.fileName, it.kind, it.sizeBytes, it.createdAt, it.success, it.message, it.transactionCount) }
    }

    override suspend fun storageUsage(): StorageUsage = withContext(io) {
        val dbFile = context.getDatabasePath(DuitKuDatabase.NAME)
        val dbBytes = listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm")).sumOf { if (it.exists()) it.length() else 0L }
        fun dirSize(dir: File) = dir.listFiles()?.sumOf { it.length() } ?: 0L
        StorageUsage(
            databaseBytes = dbBytes,
            exportBytes = dirSize(File(context.filesDir, ExportRepositoryImpl.DIR)),
            backupBytes = dirSize(internalDir),
            transactionCount = transactionDao.count(),
            statementCount = statementDao.getClosedPeriods().size,
            snapshotBytes = statementDao.snapshotBytes(),
        )
    }

    companion object {
        const val DIR = "backups"
        const val EXTENSION = "duitku"
        const val FILE_PREFIX = "DuitKu_backup_"
        const val SAFETY_PREFIX = "DuitKu_safety_"
        const val SAFETY_KEEP = 10
        const val APP_VERSION = "1.0.0"
        private const val MAX_BACKUP_BYTES = 200 * 1024 * 1024
    }
}
