package com.duitku.app.presentation.screens.export

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.duitku.app.data.export.ExportRepositoryImpl
import com.duitku.app.domain.model.ExportFormat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Aksi pada berkas hasil ekspor: bagikan, buka, dan cetak. Berkas tersimpan di filesDir/exports. */
object ExportActions {

    fun fileOf(context: Context, fileName: String): File = ExportRepositoryImpl.fileOf(context, fileName)

    private fun uriOf(context: Context, file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Lembar bagikan sistem (WhatsApp, email, Drive, dst.). */
    fun share(context: Context, fileName: String, format: ExportFormat) {
        val file = fileOf(context, fileName)
        if (!file.exists()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_STREAM, uriOf(context, file))
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Bagikan laporan").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Membuka dengan aplikasi lain (untuk CSV/XLSX yang tidak punya pratinjau di dalam aplikasi). */
    fun openExternally(context: Context, fileName: String, format: ExportFormat): Boolean {
        val file = fileOf(context, fileName)
        if (!file.exists()) return false
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriOf(context, file), format.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** Cetak langsung lewat PrintManager. Hanya untuk PDF. */
    fun print(context: Context, fileName: String) {
        val file = fileOf(context, fileName)
        if (!file.exists()) return
        val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print(fileName, PdfFileAdapter(file, fileName), null)
    }

    private class PdfFileAdapter(private val file: File, private val name: String) : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?,
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            callback.onLayoutFinished(
                PrintDocumentInfo.Builder(name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build(),
                true,
            )
        }

        override fun onWrite(pages: Array<out PageRange>?, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal?, callback: WriteResultCallback) {
            try {
                FileInputStream(file).use { input -> FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) } }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            }
        }
    }
}
