package com.duitku.app.export.pdf

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.duitku.app.core.util.MoneyFormatter

data class PdfSlice(val label: String, val value: Long, val color: Int)
data class PdfBarGroup(val label: String, val values: List<Long>)

/** Gambar grafik langsung di Canvas PDF (tanpa pustaka grafik) agar tampil sama dengan di layar. */
class PdfCharts(private val composer: PageComposer) {
    private val paints = composer.paints
    private val style = composer.style

    /** Judul kecil di atas grafik. */
    fun caption(title: String, keepWith: Float = 120f) {
        // Sertakan ruang untuk grafik di bawahnya agar judul tidak tertinggal sendirian di dasar halaman.
        composer.ensure(14f + keepWith)
        composer.text(title, composer.left, composer.y + 9f, paints.smallBold)
        composer.space(13f)
    }

    fun donut(slices: List<PdfSlice>, width: Float = composer.contentW, height: Float = 120f, currency: String = "IDR") {
        val data = slices.filter { it.value > 0 }
        composer.ensure(height + 4f)
        if (data.isEmpty()) return
        val top = composer.y
        val radius = (height / 2f) - 4f
        val cx = composer.left + radius + 6f
        val cy = top + height / 2f
        val total = data.sumOf { it.value }.toFloat()
        // Busur bergaris pada jari-jari tengah cincin: tidak perlu menimpa lubang putih.
        val thickness = radius * 0.45f
        val ringRadius = radius - thickness / 2f
        val oval = RectF(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius)
        var start = -90f
        data.forEachIndexed { i, s ->
            val sweep = 360f * s.value / total
            val p = Paint(paints.stroke(colorFor(i, s.color), thickness)).apply { strokeCap = Paint.Cap.BUTT }
            val from = start
            composer.op { it.drawArc(oval, from, sweep, false, p) }
            start += sweep
        }
        composer.text("Total", cx, cy - 1f, paints.smallMuted, Align.CENTER)
        composer.text(MoneyFormatter.compact(total.toLong()), cx, cy + 9f, paints.smallBold, Align.CENTER)

        // Legenda.
        val legendX = composer.left + radius * 2 + 24f
        val rowH = 11f
        data.take(((height) / rowH).toInt()).forEachIndexed { i, s ->
            val ly = top + 4f + i * rowH
            composer.rect(legendX, ly, legendX + 7f, ly + 7f, colorFor(i, s.color))
            val pct = Math.round(s.value * 1000f / total) / 10f
            val label = composer.fit("${s.label} (${pct.toString().replace('.', ',')}%)", paints.small, composer.right - legendX - 70f)
            composer.text(label, legendX + 11f, ly + 7f, paints.small)
            composer.text(MoneyFormatter.format(s.value, currency), composer.right, ly + 7f, paints.small, Align.RIGHT)
        }
        composer.moveTo(top + height + 4f)
    }

    fun bars(groups: List<PdfBarGroup>, seriesColors: List<Int>, height: Float = 100f, seriesNames: List<String> = emptyList()) {
        composer.ensure(height + 24f)
        if (groups.isEmpty()) return
        val top = composer.y + 8f
        val chartH = height - 22f
        val maxV = groups.maxOf { g -> g.values.maxOrNull() ?: 0L }.coerceAtLeast(1L).toFloat()
        val baseY = top + chartH
        composer.line(composer.left, baseY, composer.right, baseY, style.line, 0.6f)
        composer.text(MoneyFormatter.compact(maxV.toLong()), composer.left, top - 1f, paints.smallMuted)

        val groupW = composer.contentW / groups.size
        val seriesCount = groups.maxOf { it.values.size }.coerceAtLeast(1)
        val barW = (groupW * 0.75f / seriesCount).coerceAtMost(16f)
        val labelEvery = if (groups.size > 16) 2 else 1
        groups.forEachIndexed { gi, g ->
            val gx = composer.left + gi * groupW + (groupW - barW * seriesCount) / 2f
            g.values.forEachIndexed { si, v ->
                val h = chartH * (v / maxV)
                if (h > 0f) composer.rect(gx + si * barW, baseY - h, gx + si * barW + barW * 0.9f, baseY, colorFor(si, seriesColors.getOrElse(si) { android.graphics.Color.GRAY }))
            }
            if (gi % labelEvery == 0) composer.text(g.label, composer.left + gi * groupW + groupW / 2f, baseY + 9f, paints.smallMuted, Align.CENTER)
        }
        if (seriesNames.isNotEmpty()) {
            var lx = composer.right
            seriesNames.reversed().forEachIndexed { idx, name ->
                val si = seriesNames.size - 1 - idx
                val w = paints.small.measureText(name)
                composer.text(name, lx, top - 1f, paints.small, Align.RIGHT)
                lx -= w + 10f
                composer.rect(lx, top - 8f, lx + 6f, top - 2f, colorFor(si, seriesColors.getOrElse(si) { android.graphics.Color.GRAY }))
                lx -= 12f
            }
        }
        composer.moveTo(top + height - 4f)
    }

    /** Batang bertanda: nilai positif ke atas (hijau), negatif ke bawah (merah), garis nol di tengah. */
    fun signedBars(points: List<Pair<String, Long>>, height: Float = 100f) {
        composer.ensure(height + 24f)
        if (points.isEmpty()) return
        val top = composer.y + 8f
        val chartH = height - 22f
        val maxPos = points.maxOf { it.second }.coerceAtLeast(0L)
        val maxNeg = (-points.minOf { it.second }).coerceAtLeast(0L)
        val range = (maxPos + maxNeg).coerceAtLeast(1L).toFloat()
        val zeroY = top + chartH * (maxPos / range)
        composer.line(composer.left, zeroY, composer.right, zeroY, style.muted, 0.6f)
        composer.text("+" + MoneyFormatter.compact(maxPos), composer.left, top - 1f, paints.smallMuted)
        if (maxNeg > 0) composer.text("terendah -" + MoneyFormatter.compact(maxNeg), composer.right, top - 1f, paints.smallMuted, Align.RIGHT)

        val groupW = composer.contentW / points.size
        val barW = (groupW * 0.7f).coerceAtMost(14f)
        val posColor = if (style.useFills) style.income else style.chartColors[0]
        val negColor = if (style.useFills) style.expense else style.chartColors[2]
        points.forEachIndexed { i, (label, value) ->
            val x = composer.left + i * groupW + (groupW - barW) / 2f
            val h = chartH * (kotlin.math.abs(value) / range)
            if (h > 0f) {
                if (value >= 0) composer.rect(x, zeroY - h, x + barW, zeroY, posColor) else composer.rect(x, zeroY, x + barW, zeroY + h, negColor)
            }
            if (points.size <= 16 || i % 2 == 0) composer.text(label, composer.left + i * groupW + groupW / 2f, top + chartH + 9f, paints.smallMuted, Align.CENTER)
        }
        composer.moveTo(top + height - 4f)
    }
    fun line(points: List<Pair<String, Long>>, color: Int, height: Float = 100f, labelEvery: Int = 5) {
        composer.ensure(height + 24f)
        if (points.size < 2) return
        val top = composer.y + 8f
        val chartH = height - 22f
        val maxV = maxOf(points.maxOf { it.second }, 0L)
        val minV = minOf(points.minOf { it.second }, 0L)
        val range = (maxV - minV).coerceAtLeast(1L).toFloat()
        fun yOf(v: Long) = top + chartH * (1f - (v - minV) / range)
        val stepX = composer.contentW / (points.size - 1)

        composer.line(composer.left, yOf(0), composer.right, yOf(0), style.line, 0.6f)
        composer.text(MoneyFormatter.compact(maxV), composer.left, top - 1f, paints.smallMuted)
        if (minV < 0) composer.text(MoneyFormatter.compact(minV), composer.left, top + chartH + 8f, paints.smallMuted)

        val path = Path()
        points.forEachIndexed { i, p ->
            val x = composer.left + i * stepX
            if (i == 0) path.moveTo(x, yOf(p.second)) else path.lineTo(x, yOf(p.second))
        }
        val paint = Paint(paints.stroke(colorFor(0, color), 1.4f))
        composer.op { it.drawPath(path, paint) }
        points.forEachIndexed { i, p ->
            if (labelEvery > 0 && i % labelEvery == 0) {
                composer.text(p.first, composer.left + i * stepX, top + chartH + 9f, paints.smallMuted, Align.CENTER)
            }
        }
        composer.moveTo(top + height - 4f)
    }

    /** Tema hemat tinta memakai skala abu-abu menurut urutan, tema lain memakai warna asli. */
    private fun colorFor(index: Int, color: Int): Int =
        if (style.useFills) color else style.chartColors[index % style.chartColors.size]
}
