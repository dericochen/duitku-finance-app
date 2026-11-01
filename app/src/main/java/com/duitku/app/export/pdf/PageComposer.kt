package com.duitku.app.export.pdf

import android.graphics.Canvas
import android.graphics.Paint

/** Tujuan render halaman: PdfDocument di produksi, Bitmap di pengujian. */
interface PdfSink {
    fun startPage(pageNumber: Int, width: Int, height: Int): Canvas
    fun finishPage(pageNumber: Int)
}

enum class Align { LEFT, RIGHT, CENTER }

/**
 * Penyusun halaman A4. Semua gambar direkam sebagai operasi per halaman lebih dulu, baru dirender
 * saat jumlah halaman total diketahui, sehingga footer dapat mencetak "Hal. X dari Y".
 */
class PageComposer(
    val paints: PdfPaints,
    private val headerLeft: String,
    private val headerRight: String,
    private val footerLeft: String,
) {
    val style: PdfStyle get() = paints.style
    val pageW = 595f
    val pageH = 842f
    val left = 36f
    val right = pageW - 36f
    val contentW = right - left
    private val top = 62f
    private val bottom = pageH - 46f

    private val pages = mutableListOf<MutableList<(Canvas) -> Unit>>()
    var y = top
        private set

    val pageCount: Int get() = pages.size
    val remaining: Float get() = bottom - y

    init {
        newPage()
    }

    fun newPage() {
        pages.add(mutableListOf())
        y = top
    }

    /** Pindah halaman bila sisa ruang kurang dari [height]. Mengembalikan true bila halaman baru dibuat. */
    fun ensure(height: Float): Boolean {
        if (y + height <= bottom) return false
        newPage()
        return true
    }

    fun space(height: Float) {
        y += height
    }

    fun moveTo(newY: Float) {
        y = newY
    }

    fun op(block: (Canvas) -> Unit) {
        pages.last().add(block)
    }

    // ---------- primitif gambar ----------

    fun text(value: String, x: Float, baseline: Float, paint: Paint, align: Align = Align.LEFT) {
        val p = Paint(paint).apply {
            textAlign = when (align) {
                Align.LEFT -> Paint.Align.LEFT
                Align.RIGHT -> Paint.Align.RIGHT
                Align.CENTER -> Paint.Align.CENTER
            }
        }
        op { it.drawText(value, x, baseline, p) }
    }

    fun rect(l: Float, t: Float, r: Float, b: Float, color: Int) {
        val p = paints.fill(color)
        op { it.drawRect(l, t, r, b, p) }
    }

    fun roundRect(l: Float, t: Float, r: Float, b: Float, radius: Float, fillColor: Int?, strokeColor: Int? = null) {
        val f = fillColor?.let { paints.fill(it) }
        val s = strokeColor?.let { paints.stroke(it) }
        op {
            f?.let { p -> it.drawRoundRect(l, t, r, b, radius, radius, p) }
            s?.let { p -> it.drawRoundRect(l, t, r, b, radius, radius, p) }
        }
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int = style.line, width: Float = 0.6f) {
        val p = paints.stroke(color, width)
        op { it.drawLine(x1, y1, x2, y2, p) }
    }

    fun circle(cx: Float, cy: Float, r: Float, color: Int) {
        val p = paints.fill(color)
        op { it.drawCircle(cx, cy, r, p) }
    }

    // ---------- teks ----------

    fun measure(value: String, paint: Paint): Float = paint.measureText(value)

    /** Memotong teks dengan "..." agar muat. */
    fun fit(value: String, paint: Paint, width: Float): String {
        if (paint.measureText(value) <= width) return value
        var end = value.length
        while (end > 0 && paint.measureText(value.substring(0, end) + "...") > width) end--
        return if (end <= 0) "" else value.substring(0, end).trimEnd() + "..."
    }

    /** Membungkus teks per kata; baris terakhir dipotong bila melebihi [maxLines]. */
    fun wrap(value: String, paint: Paint, width: Float, maxLines: Int = Int.MAX_VALUE): List<String> {
        if (value.isBlank()) return listOf("")
        val lines = mutableListOf<String>()
        value.split('\n').forEach { paragraph ->
            var current = ""
            for (word in paragraph.split(' ')) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(candidate) <= width) {
                    current = candidate
                } else {
                    if (current.isNotEmpty()) lines += current
                    // Kata yang lebih panjang dari lebar dipotong paksa.
                    current = if (paint.measureText(word) > width) fit(word, paint, width) else word
                }
            }
            lines += current
        }
        if (lines.size <= maxLines) return lines
        val kept = lines.take(maxLines).toMutableList()
        kept[maxLines - 1] = fit(kept[maxLines - 1] + " " + lines.drop(maxLines).joinToString(" "), paint, width)
        return kept
    }

    /** Paragraf teks yang berpindah halaman bila perlu. Mengembalikan tinggi yang dipakai. */
    fun paragraph(value: String, paint: Paint, width: Float = contentW, x: Float = left, lineHeight: Float = paint.textSize * 1.35f) {
        for (line in wrap(value, paint, width)) {
            ensure(lineHeight)
            text(line, x, y + paint.textSize, paint)
            y += lineHeight
        }
    }

    /** Judul bagian dengan latar berwarna. Menjaga minimal [keepWith] ruang di bawahnya agar tidak yatim. */
    fun section(title: String, keepWith: Float = 60f) {
        ensure(26f + keepWith)
        y += 6f
        if (style.useFills) {
            rect(left, y, right, y + 18f, style.accent)
            text(title, left + 6f, y + 13f, paints.h1)
        } else {
            text(title.uppercase(), left, y + 13f, Paint(paints.h2).apply { textSize = 11f })
            line(left, y + 18f, right, y + 18f, Color_BLACK, 1.2f)
        }
        y += 24f
    }

    // ---------- render akhir ----------

    fun render(sink: PdfSink) {
        val total = pages.size
        pages.forEachIndexed { index, ops ->
            val number = index + 1
            val canvas = sink.startPage(number, pageW.toInt(), pageH.toInt())
            drawChrome(canvas, number, total)
            ops.forEach { it(canvas) }
            sink.finishPage(number)
        }
    }

    private fun drawChrome(canvas: Canvas, number: Int, total: Int) {
        val muted = paints.with(paints.smallMuted)
        canvas.drawText(headerLeft, left, 34f, paints.with(paints.smallBold, style.accent))
        canvas.drawText(headerRight, right, 34f, paints.with(muted, align = Paint.Align.RIGHT))
        canvas.drawLine(left, 42f, right, 42f, paints.stroke(style.accent, 1f))
        canvas.drawLine(left, pageH - 34f, right, pageH - 34f, paints.stroke(style.line, 0.6f))
        canvas.drawText(footerLeft, left, pageH - 22f, muted)
        canvas.drawText("Hal. $number dari $total", right, pageH - 22f, paints.with(muted, align = Paint.Align.RIGHT))
    }

    private companion object {
        const val Color_BLACK = android.graphics.Color.BLACK
    }
}
