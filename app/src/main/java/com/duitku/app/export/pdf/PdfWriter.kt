package com.duitku.app.export.pdf

import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Mengubah hasil render halaman menjadi byte PDF. Dipisah agar pipeline ekspor dapat diuji di JVM. */
interface PdfWriter {
    fun write(render: (PdfSink) -> Int): ByteArray
}

/** Implementasi produksi memakai android.graphics.pdf.PdfDocument. */
@Singleton
class AndroidPdfWriter @Inject constructor() : PdfWriter {
    override fun write(render: (PdfSink) -> Int): ByteArray {
        val sink = PdfDocumentSink()
        try {
            render(sink)
            return ByteArrayOutputStream().also { sink.writeTo(it) }.toByteArray()
        } finally {
            sink.close()
        }
    }
}
