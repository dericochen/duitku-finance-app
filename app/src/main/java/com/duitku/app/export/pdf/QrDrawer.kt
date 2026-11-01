package com.duitku.app.export.pdf

import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Menggambar kode QR sebagai kotak-kotak vektor (tajam di PDF, tanpa Bitmap). */
object QrDrawer {
    fun draw(composer: PageComposer, payload: String, x: Float, y: Float, size: Float) {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0)
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 0, 0, hints)
        val cells = matrix.width
        val cell = size / cells
        composer.rect(x - 3f, y - 3f, x + size + 3f, y + size + 3f, Color.WHITE)
        for (row in 0 until matrix.height) {
            for (col in 0 until cells) {
                if (matrix[col, row]) {
                    composer.rect(x + col * cell, y + row * cell, x + (col + 1) * cell + 0.15f, y + (row + 1) * cell + 0.15f, Color.BLACK)
                }
            }
        }
    }

    /** Untuk pengujian: apakah payload dapat dikodekan menjadi matriks QR. */
    fun modules(payload: String): Int =
        QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 0, 0).width
}
