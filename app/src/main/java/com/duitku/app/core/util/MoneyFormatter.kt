package com.duitku.app.core.util

import kotlin.math.abs

/**
 * Format uang bergaya Indonesia: "Rp 1.250.000".
 *
 * Nominal disimpan sebagai [Long] satuan utuh (tanpa sen) agar perhitungan saldo selalu tepat.
 * Pilihan mata uang hanya memengaruhi simbol tampilan.
 */
object MoneyFormatter {
    private val symbols = linkedMapOf(
        "IDR" to "Rp",
        "USD" to "$",
        "EUR" to "€",
        "GBP" to "£",
        "SGD" to "S$",
        "MYR" to "RM",
        "JPY" to "¥",
    )

    val supportedCodes: List<String> get() = symbols.keys.toList()

    fun symbolOf(code: String): String = symbols[code] ?: code

    fun format(
        amount: Long,
        currencyCode: String = "IDR",
        withSymbol: Boolean = true,
        showPlusSign: Boolean = false,
    ): String {
        val magnitude = if (amount == Long.MIN_VALUE) Long.MAX_VALUE else abs(amount)
        val digits = groupThousands(magnitude)
        val sign = when {
            amount < 0 -> "-"
            showPlusSign && amount > 0 -> "+"
            else -> ""
        }
        return if (withSymbol) "$sign${symbolOf(currencyCode)} $digits" else "$sign$digits"
    }

    /** Versi ringkas untuk label grafik: 1,2 jt / 350 rb / 2,5 M. */
    fun compact(amount: Long): String {
        val magnitude = abs(amount)
        val sign = if (amount < 0) "-" else ""
        return when {
            magnitude >= 1_000_000_000L -> "$sign${trim(magnitude / 1_000_000_000.0)} M"
            magnitude >= 1_000_000L -> "$sign${trim(magnitude / 1_000_000.0)} jt"
            magnitude >= 1_000L -> "$sign${trim(magnitude / 1_000.0)} rb"
            else -> "$sign$magnitude"
        }
    }

    fun groupThousands(value: Long): String {
        val raw = value.toString()
        val sb = StringBuilder(raw.length + raw.length / 3)
        raw.forEachIndexed { index, ch ->
            if (index > 0 && (raw.length - index) % 3 == 0) sb.append('.')
            sb.append(ch)
        }
        return sb.toString()
    }

    /**
     * Mengubah teks input (boleh berisi titik pemisah ribuan, spasi, "Rp") menjadi Long.
     * Mengembalikan null bila tidak ada digit atau nilai melebihi batas Long.
     */
    fun parse(input: String): Long? {
        val digits = input.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        return digits.toLongOrNull()
    }

    private fun trim(value: Double): String {
        val rounded = Math.round(value * 10) / 10.0
        val text = if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString().replace('.', ',')
        return text
    }
}
