package com.duitku.app.domain.model

/** Jenis dompet. Disimpan di Room sebagai nama enum (String). */
enum class WalletType(val label: String) {
    CASH("Tunai"),
    BANK("Bank"),
    E_WALLET("E-Wallet"),
    CREDIT_CARD("Kartu Kredit"),
    SAVINGS("Tabungan"),
}

enum class TransactionType(val label: String) {
    INCOME("Pemasukan"),
    EXPENSE("Pengeluaran"),
    TRANSFER("Transfer"),
}

enum class CategoryKind(val label: String) {
    INCOME("Pemasukan"),
    EXPENSE("Pengeluaran"),
}

enum class RecurrenceFrequency(val label: String) {
    DAILY("Harian"),
    WEEKLY("Mingguan"),
    MONTHLY("Bulanan"),
    YEARLY("Tahunan"),
}

enum class DebtKind(val label: String) {
    /** Saya berutang kepada pihak lain. */
    DEBT("Utang"),

    /** Pihak lain berutang kepada saya. */
    RECEIVABLE("Piutang"),
}

/**
 * Status arsip bulanan.
 * - OPEN: bulan dibuka kembali (boleh diedit); snapshot lama belum berlaku.
 * - CLOSED: bulan tertutup, read-only, snapshot + checksum berlaku.
 * - SUPERSEDED: revisi lama yang sudah digantikan revisi lebih baru (tetap disimpan sebagai riwayat).
 */
enum class StatementStatus { OPEN, CLOSED, SUPERSEDED }

enum class ExportFormat(val extension: String, val mimeType: String) {
    PDF("pdf", "application/pdf"),
    CSV("csv", "text/csv"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
}

enum class ExportKind(val label: String) {
    MONTHLY("Mutasi Bulanan"),
    RANGE("Rentang Periode"),
    ANNUAL("Laporan Tahunan"),
}

enum class BackupKind(val label: String) {
    MANUAL("Manual"),
    AUTO("Otomatis"),
    PRE_PURGE("Sebelum pembersihan"),
    PRE_RESTORE("Sebelum pemulihan"),
    PRE_RESET("Sebelum reset"),
    ANNUAL_ARCHIVE("Arsip tahunan"),
}

enum class ThemeMode(val label: String) {
    SYSTEM("Ikuti sistem"),
    LIGHT("Terang"),
    DARK("Gelap"),
}

enum class BackupFrequency(val label: String) {
    OFF("Nonaktif"),
    DAILY("Harian"),
    WEEKLY("Mingguan"),
    MONTHLY("Bulanan"),
}

enum class PdfTheme(val label: String) {
    CLASSIC("Klasik"),
    MODERN("Modern"),
    ECO("Hemat Tinta"),
}

/** Tindakan untuk data yang melewati masa retensi. */
enum class RetentionAction(val label: String) {
    /** Setelah arsip dibuat, minta konfirmasi pengguna sebelum membersihkan detail transaksi. */
    ASK_BEFORE_PURGE("Tanya sebelum membersihkan"),

    /** Hanya tandai sebagai arsip, tidak pernah menghapus detail transaksi. */
    ARCHIVE_ONLY("Pindahkan ke arsip saja"),
}
