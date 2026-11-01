package com.duitku.app.data.local

import javax.inject.Inject
import javax.inject.Singleton

/** Hasil pemeriksaan integritas database. */
data class IntegrityReport(
    val isOk: Boolean,
    val integrityMessages: List<String>,
    val foreignKeyViolations: Int,
) {
    val summary: String
        get() = when {
            isOk -> "Database sehat"
            foreignKeyViolations > 0 -> "Ditemukan $foreignKeyViolations pelanggaran relasi data"
            else -> integrityMessages.joinToString("; ")
        }
}

/**
 * Menjalankan `PRAGMA integrity_check` dan `PRAGMA foreign_key_check` saat aplikasi dibuka.
 * Dijalankan di thread IO oleh pemanggil.
 */
@Singleton
class DatabaseIntegrityChecker @Inject constructor(
    private val database: DuitKuDatabase,
) {
    fun check(): IntegrityReport {
        val db = database.openHelper.readableDatabase
        val messages = mutableListOf<String>()
        db.query("PRAGMA integrity_check").use { cursor ->
            while (cursor.moveToNext()) messages += cursor.getString(0)
        }
        var violations = 0
        db.query("PRAGMA foreign_key_check").use { cursor ->
            while (cursor.moveToNext()) violations++
        }
        val integrityOk = messages.size == 1 && messages.first().equals("ok", ignoreCase = true)
        return IntegrityReport(
            isOk = integrityOk && violations == 0,
            integrityMessages = messages,
            foreignKeyViolations = violations,
        )
    }
}
