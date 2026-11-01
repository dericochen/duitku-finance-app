package com.duitku.app.data.backup

import com.duitku.app.data.local.entity.AuditLogEntity
import com.duitku.app.data.local.entity.BudgetEntity
import com.duitku.app.data.local.entity.CategoryEntity
import com.duitku.app.data.local.entity.DebtEntity
import com.duitku.app.data.local.entity.DebtPaymentEntity
import com.duitku.app.data.local.entity.GoalContributionEntity
import com.duitku.app.data.local.entity.MonthlyStatementEntity
import com.duitku.app.data.local.entity.MonthlyWalletBalanceEntity
import com.duitku.app.data.local.entity.RecurringTransactionEntity
import com.duitku.app.data.local.entity.SavingGoalEntity
import com.duitku.app.data.local.entity.TransactionEntity
import com.duitku.app.data.local.entity.WalletEntity
import com.duitku.app.domain.usecase.statement.Checksum
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupSettings(
    val ownerName: String = "",
    val currencyCode: String = "IDR",
    val monthStartDay: Int = 1,
    val retentionMonths: Int = 12,
)

/** Isi backup: semua tabel data milik pengguna (riwayat ekspor & catatan backup bersifat lokal perangkat dan tidak ikut). */
@Serializable
data class BackupPayload(
    val wallets: List<WalletEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val recurring: List<RecurringTransactionEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val goals: List<SavingGoalEntity> = emptyList(),
    val contributions: List<GoalContributionEntity> = emptyList(),
    val debts: List<DebtEntity> = emptyList(),
    val payments: List<DebtPaymentEntity> = emptyList(),
    val statements: List<MonthlyStatementEntity> = emptyList(),
    val walletBalances: List<MonthlyWalletBalanceEntity> = emptyList(),
    val auditLogs: List<AuditLogEntity> = emptyList(),
    val settings: BackupSettings = BackupSettings(),
)

/** Amplop file .duitku. [payload] adalah string JSON agar checksum dihitung atas byte yang persis sama. */
@Serializable
data class BackupEnvelope(
    val format: String = FORMAT,
    val envelopeVersion: Int = 1,
    val dbVersion: Int,
    val appVersion: String,
    val createdAt: Long,
    val transactionCount: Int,
    val checksum: String,
    val payload: String,
) {
    companion object {
        const val FORMAT = "duitku-backup"
    }
}

sealed interface DecodeResult {
    data class Ok(val envelope: BackupEnvelope, val payload: BackupPayload, val sizeBytes: Long) : DecodeResult
    data class Error(val reason: String) : DecodeResult
}

/** Serialisasi, kompresi GZIP, dan validasi file backup. Murni (tanpa Android) sehingga mudah diuji. */
object BackupCodec {
    val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    fun encode(payload: BackupPayload, dbVersion: Int, appVersion: String, createdAt: Long): ByteArray {
        val payloadJson = json.encodeToString(BackupPayload.serializer(), payload)
        val envelope = BackupEnvelope(
            dbVersion = dbVersion, appVersion = appVersion, createdAt = createdAt,
            transactionCount = payload.transactions.size, checksum = Checksum.sha256Hex(payloadJson), payload = payloadJson,
        )
        val raw = json.encodeToString(BackupEnvelope.serializer(), envelope).toByteArray(Charsets.UTF_8)
        return ByteArrayOutputStream().also { bos -> GZIPOutputStream(bos).use { it.write(raw) } }.toByteArray()
    }

    fun decode(bytes: ByteArray, currentDbVersion: Int): DecodeResult {
        val text = try {
            val isGzip = bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
            if (isGzip) GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }.toString(Charsets.UTF_8) else bytes.toString(Charsets.UTF_8)
        } catch (e: Exception) {
            return DecodeResult.Error("Berkas rusak atau bukan backup DuitKu")
        }
        val envelope = try {
            json.decodeFromString(BackupEnvelope.serializer(), text)
        } catch (e: Exception) {
            return DecodeResult.Error("Berkas bukan backup DuitKu yang valid")
        }
        if (envelope.format != BackupEnvelope.FORMAT) return DecodeResult.Error("Format berkas tidak dikenali")
        if (envelope.dbVersion > currentDbVersion) {
            return DecodeResult.Error(
                "Backup dibuat oleh versi aplikasi yang lebih baru (database v${envelope.dbVersion}, aplikasi ini v$currentDbVersion). " +
                    "Perbarui DuitKu terlebih dahulu.",
            )
        }
        if (envelope.dbVersion < 1) return DecodeResult.Error("Versi backup tidak valid")
        if (Checksum.sha256Hex(envelope.payload) != envelope.checksum) {
            return DecodeResult.Error("Checksum tidak cocok: berkas berubah atau rusak setelah dibuat")
        }
        val payload = try {
            json.decodeFromString(BackupPayload.serializer(), envelope.payload)
        } catch (e: Exception) {
            return DecodeResult.Error("Isi backup tidak dapat dibaca")
        }
        return DecodeResult.Ok(envelope, payload, bytes.size.toLong())
    }
}
