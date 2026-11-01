package com.duitku.app.export.pdf

import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

/** Menulis halaman ke [PdfDocument] Android (A4 595x842 pt). */
class PdfDocumentSink : PdfSink {
    private val document = PdfDocument()
    private var current: PdfDocument.Page? = null

    override fun startPage(pageNumber: Int, width: Int, height: Int): Canvas {
        val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, pageNumber).create())
        current = page
        return page.canvas
    }

    override fun finishPage(pageNumber: Int) {
        current?.let { document.finishPage(it) }
        current = null
    }

    fun writeTo(out: OutputStream) {
        document.writeTo(out)
    }

    fun close() {
        document.close()
    }
}
