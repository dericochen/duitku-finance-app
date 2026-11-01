package com.duitku.app.export.pdf

import android.graphics.Paint

data class Col(
    val title: String,
    val width: Float,
    val align: Align = Align.LEFT,
    /** Jumlah baris maksimum sel (teks dibungkus bila > 1). */
    val maxLines: Int = 1,
)

data class Cell(
    val text: String,
    val color: Int? = null,
    val bold: Boolean = false,
)

enum class RowKind { NORMAL, DAY_HEADER, SUBTOTAL, TOTAL }

data class TableRow(
    val cells: List<Cell>,
    val kind: RowKind = RowKind.NORMAL,
    /** Gabungkan sel pertama melintasi seluruh lebar (untuk judul tanggal). */
    val spanFirst: Boolean = false,
)

fun cell(text: String, color: Int? = null, bold: Boolean = false) = Cell(text, color, bold)

/**
 * Menggambar tabel dengan header yang diulang di setiap halaman, baris selang-seling, dan tinggi baris
 * menyesuaikan sel yang dibungkus.
 */
class PdfTable(
    private val composer: PageComposer,
    private val columns: List<Col>,
) {
    private val paints = composer.paints
    private val style = composer.style
    private val padding = 3f
    private val lineHeight = 8.6f
    private var zebra = false

    private val scale: Float = composer.contentW / columns.sumOf { it.width.toDouble() }.toFloat()
    private val widths = columns.map { it.width * scale }

    fun draw(rows: List<TableRow>) {
        drawHeader()
        zebra = false
        for ((index, row) in rows.withIndex()) {
            val lines = rowLines(row)
            val height = (lines * lineHeight + padding * 2)
            // Judul tanggal tidak boleh tertinggal sendirian di dasar halaman: sertakan baris berikutnya.
            val lookahead = if (row.kind == RowKind.DAY_HEADER) rows.getOrNull(index + 1)?.let { rowLines(it) * lineHeight + padding * 2 } ?: 0f else 0f
            // Cadangkan ruang untuk header baru bila pindah halaman.
            if (composer.ensure(height + lookahead + 2f)) drawHeader()
            drawRow(row, height)
            if (row.kind == RowKind.NORMAL) zebra = !zebra else zebra = false
        }
        composer.space(4f)
    }

    private fun drawHeader() {
        val h = 14f
        composer.ensure(h + 24f)
        val top = composer.y
        if (style.useFills) composer.rect(composer.left, top, composer.right, top + h, style.headerBg)
        var x = composer.left
        columns.forEachIndexed { i, col ->
            val w = widths[i]
            drawCell(col.title, x, w, top + 9.6f, paints.header.let { p -> if (style.useFills) p else paints.cellBold }, col.align)
            x += w
        }
        if (!style.useFills) {
            composer.line(composer.left, top + h, composer.right, top + h, android.graphics.Color.BLACK, 1f)
        }
        composer.moveTo(top + h)
    }

    private fun rowLines(row: TableRow): Int {
        if (row.spanFirst) return 1
        var max = 1
        row.cells.forEachIndexed { i, c ->
            val col = columns.getOrNull(i) ?: return@forEachIndexed
            if (col.maxLines > 1) {
                val paint = if (c.bold || row.kind != RowKind.NORMAL) paints.cellBold else paints.cell
                max = maxOf(max, composer.wrap(c.text, paint, widths[i] - padding * 2, col.maxLines).size)
            }
        }
        return max
    }

    private fun drawRow(row: TableRow, height: Float) {
        val top = composer.y
        val bold = row.kind != RowKind.NORMAL
        when (row.kind) {
            RowKind.DAY_HEADER -> if (style.useFills) composer.rect(composer.left, top, composer.right, top + height, style.dayBg)
            RowKind.TOTAL -> if (style.useFills) composer.rect(composer.left, top, composer.right, top + height, style.dayBg)
            RowKind.SUBTOTAL, RowKind.NORMAL -> if (zebra && style.useFills) composer.rect(composer.left, top, composer.right, top + height, style.altRow)
        }
        if (row.spanFirst) {
            val c = row.cells.first()
            composer.text(composer.fit(c.text, paints.cellBold, composer.contentW - padding * 2), composer.left + padding, top + padding + 7f, paints.with(paints.cellBold, c.color))
        } else {
            var x = composer.left
            row.cells.forEachIndexed { i, c ->
                val col = columns.getOrNull(i) ?: return@forEachIndexed
                val base = if (c.bold || bold) paints.cellBold else paints.cell
                val paint = if (c.color != null) paints.with(base, c.color) else base
                drawCell(c.text, x, widths[i], top + padding + 7f, paint, col.align, col.maxLines)
                x += widths[i]
            }
        }
        val lineColor = if (style.useFills) style.line else android.graphics.Color.LTGRAY
        composer.line(composer.left, top + height, composer.right, top + height, lineColor, if (row.kind == RowKind.TOTAL) 1f else 0.3f)
        composer.moveTo(top + height)
    }

    private fun drawCell(text: String, x: Float, width: Float, baseline: Float, paint: Paint, align: Align, maxLines: Int = 1) {
        val inner = width - padding * 2
        val lines = if (maxLines > 1) composer.wrap(text, paint, inner, maxLines) else listOf(composer.fit(text, paint, inner))
        lines.forEachIndexed { i, line ->
            val lineBaseline = baseline + i * lineHeight
            when (align) {
                Align.LEFT -> composer.text(line, x + padding, lineBaseline, paint, Align.LEFT)
                Align.RIGHT -> composer.text(line, x + width - padding, lineBaseline, paint, Align.RIGHT)
                Align.CENTER -> composer.text(line, x + width / 2, lineBaseline, paint, Align.CENTER)
            }
        }
    }
}
