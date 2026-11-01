package com.duitku.app.export.pdf

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.duitku.app.domain.model.PdfTheme

/** Palet warna tema PDF. Tema Hemat Tinta hanya memakai hitam-putih dan abu-abu. */
data class PdfStyle(
    val theme: PdfTheme,
    val accent: Int,
    val headerBg: Int,
    val headerText: Int,
    val altRow: Int,
    val dayBg: Int,
    val income: Int,
    val expense: Int,
    val transfer: Int,
    val warning: Int,
    val text: Int,
    val muted: Int,
    val line: Int,
    val chartColors: List<Int>,
    val useFills: Boolean,
) {
    companion object {
        fun of(theme: PdfTheme): PdfStyle = when (theme) {
            PdfTheme.CLASSIC -> PdfStyle(
                theme, accent = Color.rgb(0x1F, 0x3A, 0x5F), headerBg = Color.rgb(0x1F, 0x3A, 0x5F), headerText = Color.WHITE,
                altRow = Color.rgb(0xF3, 0xF5, 0xF8), dayBg = Color.rgb(0xDD, 0xE3, 0xEC),
                income = Color.rgb(0x1B, 0x7A, 0x43), expense = Color.rgb(0xC6, 0x28, 0x28), transfer = Color.rgb(0x15, 0x65, 0xC0),
                warning = Color.rgb(0xB2, 0x6A, 0x00), text = Color.rgb(0x20, 0x20, 0x20), muted = Color.rgb(0x6B, 0x72, 0x80),
                line = Color.rgb(0xC9, 0xCF, 0xD8),
                chartColors = listOf(0xFF1F3A5F, 0xFFC62828, 0xFF1B7A43, 0xFFE65100, 0xFF6A1B9A, 0xFF00897B, 0xFFF9A825, 0xFF6D4C41).map { it.toInt() },
                useFills = true,
            )
            PdfTheme.MODERN -> PdfStyle(
                theme, accent = Color.rgb(0x0B, 0x6B, 0x4F), headerBg = Color.rgb(0x0B, 0x6B, 0x4F), headerText = Color.WHITE,
                altRow = Color.rgb(0xEE, 0xF6, 0xF2), dayBg = Color.rgb(0xCF, 0xE8, 0xDC),
                income = Color.rgb(0x1B, 0x7A, 0x43), expense = Color.rgb(0xD3, 0x2F, 0x2F), transfer = Color.rgb(0x02, 0x77, 0xBD),
                warning = Color.rgb(0xE6, 0x7E, 0x00), text = Color.rgb(0x1B, 0x1F, 0x1D), muted = Color.rgb(0x6F, 0x79, 0x73),
                line = Color.rgb(0xCF, 0xD9, 0xD3),
                chartColors = listOf(0xFF0B6B4F, 0xFFE65100, 0xFF0277BD, 0xFFAD1457, 0xFFF9A825, 0xFF6A1B9A, 0xFF00897B, 0xFF546E7A).map { it.toInt() },
                useFills = true,
            )
            PdfTheme.ECO -> PdfStyle(
                theme, accent = Color.BLACK, headerBg = Color.WHITE, headerText = Color.BLACK,
                altRow = Color.WHITE, dayBg = Color.WHITE,
                income = Color.BLACK, expense = Color.BLACK, transfer = Color.DKGRAY, warning = Color.BLACK,
                text = Color.BLACK, muted = Color.DKGRAY, line = Color.GRAY,
                chartColors = listOf(0xFF000000, 0xFF555555, 0xFF888888, 0xFFAAAAAA, 0xFFCCCCCC, 0xFF333333, 0xFF777777, 0xFFBBBBBB).map { it.toInt() },
                useFills = false,
            )
        }
    }
}

/** Kumpulan Paint yang dipakai bersama. Font sistem (Roboto) mendukung "Rp" dan huruf Indonesia. */
class PdfPaints(val style: PdfStyle) {
    private val regular: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    private val bold: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

    private fun paint(size: Float, color: Int, boldFace: Boolean = false, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (boldFace) bold else regular
            textAlign = align
        }

    val title = paint(20f, style.accent, true)
    val subtitle = paint(11f, style.muted)
    val h1 = paint(12f, style.headerText, true)
    val h2 = paint(10f, style.accent, true)
    val body = paint(9f, style.text)
    val bodyBold = paint(9f, style.text, true)
    val small = paint(7.5f, style.text)
    val smallBold = paint(7.5f, style.text, true)
    val smallMuted = paint(7f, style.muted)
    val cell = paint(7f, style.text)
    val cellBold = paint(7f, style.text, true)
    val header = paint(7f, style.headerText, true)
    val big = paint(14f, style.text, true)

    fun fill(color: Int) = Paint().apply { this.color = color; style = Paint.Style.FILL; isAntiAlias = true }
    fun stroke(color: Int, width: Float = 0.6f) = Paint().apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = width; isAntiAlias = true }

    /** Salinan paint dengan warna dan perataan berbeda. */
    fun with(base: Paint, color: Int? = null, align: Paint.Align? = null): Paint = Paint(base).apply {
        color?.let { this.color = it }
        align?.let { textAlign = it }
    }
}
