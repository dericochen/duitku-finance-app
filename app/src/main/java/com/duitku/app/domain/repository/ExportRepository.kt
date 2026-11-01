package com.duitku.app.domain.repository

import com.duitku.app.domain.model.ExportFormat
import com.duitku.app.domain.model.ExportKind
import com.duitku.app.domain.model.ExportOptions
import com.duitku.app.domain.model.ExportProgress
import com.duitku.app.domain.model.ExportResult
import kotlinx.coroutines.flow.Flow

data class ExportRecord(
    val id: Long,
    val fileName: String,
    val format: ExportFormat,
    val kind: ExportKind,
    val fromPeriodYm: Int,
    val toPeriodYm: Int,
    val documentNumber: String?,
    val sizeBytes: Long,
    val createdAt: Long,
    val fileExists: Boolean,
)

interface ExportRepository {
    /** Membuat berkas di latar belakang (Dispatchers.IO) dan melaporkan kemajuan. */
    suspend fun export(options: ExportOptions, onProgress: (ExportProgress) -> Unit = {}): ExportResult

    fun observeHistory(): Flow<List<ExportRecord>>
    suspend fun getRecord(id: Long): ExportRecord?
    suspend fun deleteRecord(id: Long)

    /** Menyalin berkas hasil ekspor ke URI tujuan (hasil Storage Access Framework). */
    suspend fun copyTo(id: Long, destinationUri: String): Boolean
}
