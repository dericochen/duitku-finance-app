package com.duitku.app.domain.model

/** Kesalahan validasi form transaksi. Pesan ditampilkan oleh layer presentation. */
enum class TransactionError(val message: String) {
    AMOUNT_EMPTY("Nominal wajib diisi"),
    AMOUNT_NOT_POSITIVE("Nominal harus lebih dari 0"),
    AMOUNT_TOO_LARGE("Nominal terlalu besar"),
    WALLET_MISSING("Pilih dompet"),
    TO_WALLET_MISSING("Pilih dompet tujuan"),
    SAME_WALLET("Dompet asal dan tujuan tidak boleh sama"),
    CATEGORY_MISSING("Pilih kategori"),
    NOTE_TOO_LONG("Catatan maksimal ${TransactionRules.MAX_NOTE_LENGTH} karakter"),
    TOO_MANY_TAGS("Maksimal ${TransactionRules.MAX_TAGS} tag"),
    TAG_TOO_LONG("Tag maksimal ${TransactionRules.MAX_TAG_LENGTH} karakter"),
    DATE_OUT_OF_RANGE("Tanggal di luar rentang yang diizinkan"),
}

object TransactionRules {
    const val MAX_AMOUNT = 999_999_999_999L
    const val MAX_NOTE_LENGTH = 200
    const val MAX_TAGS = 10
    const val MAX_TAG_LENGTH = 24
    const val MIN_YEAR = 2000
    const val MAX_YEAR = 2100

    /** Membersihkan tag: trim, buang '#', '|', huruf kecil, hapus duplikat. */
    fun normalizeTag(raw: String): String =
        raw.trim().trimStart('#').replace("|", "").trim().lowercase()

    fun normalizeTags(raw: List<String>): List<String> =
        raw.map(::normalizeTag).filter { it.isNotEmpty() }.distinct()
}
