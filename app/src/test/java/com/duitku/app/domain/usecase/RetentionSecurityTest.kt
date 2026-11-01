package com.duitku.app.domain.usecase

import com.duitku.app.core.util.toEpochMillis
import com.duitku.app.domain.model.AppSettings
import com.duitku.app.domain.usecase.retention.RetentionPlanner
import com.duitku.app.domain.usecase.security.PinAttemptPolicy
import com.duitku.app.domain.usecase.security.PinHasher
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetentionSecurityTest {

    private val today = LocalDate.of(2026, 11, 15)
    private fun millis(y: Int, m: Int, d: Int) = LocalDateTime.of(y, m, d, 10, 0).toEpochMillis()

    @Test
    fun `retensi 12 bulan menyimpan 12 bulan penuh ditambah bulan berjalan`() {
        val w = RetentionPlanner.window(12, 1, today)
        assertEquals(YearMonth.of(2025, 11), w.firstKeptPeriod)
        assertEquals(millis(2025, 11, 1) - 10 * 3_600_000L, w.cutoffMillis) // 1 Nov 2025 00:00 (tes memakai jam 10.00 jadi dikurangi 10 jam)
        // Nov 2025 .. Okt 2026 = 12 bulan penuh + Nov 2026 berjalan
        assertFalse(millis(2025, 11, 1) < w.cutoffMillis!!)
    }

    @Test
    fun `selamanya tidak pernah kedaluwarsa`() {
        val w = RetentionPlanner.window(AppSettings.RETENTION_FOREVER, 1, today)
        assertTrue(w.isForever)
        assertNull(w.cutoffMillis)
        assertTrue(RetentionPlanner.expiredPeriods(millis(2000, 1, 1), w, 1).isEmpty())
        assertFalse(RetentionPlanner.expiresSoon(millis(2000, 1, 1), AppSettings.RETENTION_FOREVER, 1, today))
    }

    @Test
    fun `daftar periode kedaluwarsa tepat sebelum jendela`() {
        val w = RetentionPlanner.window(12, 1, today)
        val expired = RetentionPlanner.expiredPeriods(millis(2025, 8, 20), w, 1)
        assertEquals(listOf(YearMonth.of(2025, 8), YearMonth.of(2025, 9), YearMonth.of(2025, 10)), expired)
        assertTrue(RetentionPlanner.expiredPeriods(millis(2025, 11, 2), w, 1).isEmpty())
        assertTrue(RetentionPlanner.expiredPeriods(null, w, 1).isEmpty())
    }

    @Test
    fun `retensi mengikuti tanggal awal bulan`() {
        // Awal bulan 25: periode berjalan pada 15 Nov 2026 adalah "November" (25 Okt - 24 Nov).
        val w = RetentionPlanner.window(12, 25, today)
        assertEquals(YearMonth.of(2025, 11), w.firstKeptPeriod)
        assertEquals(LocalDate.of(2025, 10, 25), java.time.Instant.ofEpochMilli(w.cutoffMillis!!).atZone(java.time.ZoneId.systemDefault()).toLocalDate())
    }

    @Test
    fun `peringatan 30 hari sebelum data kedaluwarsa`() {
        // Data tertua 10 Nov 2025: masih aman hari ini (cutoff 1 Nov 2025) tapi lewat dalam 30 hari (cutoff 1 Des 2025).
        assertTrue(RetentionPlanner.expiresSoon(millis(2025, 11, 10), 12, 1, today))
        // Data tertua 10 Des 2025: baru kedaluwarsa lebih dari 30 hari lagi.
        assertFalse(RetentionPlanner.expiresSoon(millis(2025, 12, 10), 12, 1, today))
        // Sudah kedaluwarsa hari ini: bukan "akan", sudah.
        assertFalse(RetentionPlanner.expiresSoon(millis(2025, 10, 10), 12, 1, today))
        assertFalse(RetentionPlanner.expiresSoon(null, 12, 1, today))
    }

    @Test
    fun `retensi 24 dan 36 bulan lebih panjang`() {
        assertEquals(YearMonth.of(2024, 11), RetentionPlanner.window(24, 1, today).firstKeptPeriod)
        assertEquals(YearMonth.of(2023, 11), RetentionPlanner.window(36, 1, today).firstKeptPeriod)
    }

    // ---------- PIN ----------

    @Test
    fun `hash PIN tidak menyimpan PIN dan salt berbeda menghasilkan hash berbeda`() {
        val a = PinHasher.hash("4827")
        val b = PinHasher.hash("4827")
        assertNotEquals(a.salt, b.salt)
        assertNotEquals(a.hash, b.hash)
        assertFalse(a.hash.contains("4827"))
        assertEquals(64, a.hash.length)
    }

    @Test
    fun `verifikasi PIN benar dan salah`() {
        val h = PinHasher.hash("482915")
        assertTrue(PinHasher.verify("482915", h.hash, h.salt))
        assertFalse(PinHasher.verify("482916", h.hash, h.salt))
        assertFalse(PinHasher.verify("", h.hash, h.salt))
        assertFalse(PinHasher.verify("482915", "", ""))
    }

    @Test
    fun `format dan kekuatan PIN`() {
        assertTrue(PinHasher.isValidFormat("1234"))
        assertTrue(PinHasher.isValidFormat("123456"))
        assertFalse(PinHasher.isValidFormat("123"))
        assertFalse(PinHasher.isValidFormat("1234567"))
        assertFalse(PinHasher.isValidFormat("12a4"))
        assertTrue(PinHasher.isWeak("0000"))
        assertTrue(PinHasher.isWeak("1234"))
        assertTrue(PinHasher.isWeak("9876"))
        assertFalse(PinHasher.isWeak("4827"))
    }

    @Test
    fun `jeda percobaan PIN membesar setelah gagal berulang`() {
        assertEquals(0, PinAttemptPolicy.lockoutSeconds(4))
        assertEquals(30, PinAttemptPolicy.lockoutSeconds(5))
        assertEquals(60, PinAttemptPolicy.lockoutSeconds(6))
        assertEquals(300, PinAttemptPolicy.lockoutSeconds(7))
        assertEquals(900, PinAttemptPolicy.lockoutSeconds(8))
        assertEquals(3_600, PinAttemptPolicy.lockoutSeconds(20))
        assertEquals(20_000, PinAttemptPolicy.remainingLockMillis(5, lastFailureAtMillis = 1_000_000, nowMillis = 1_010_000))
        assertEquals(0, PinAttemptPolicy.remainingLockMillis(5, 1_000_000, 1_031_000))
        assertEquals(0, PinAttemptPolicy.remainingLockMillis(2, 1_000_000, 1_000_001))
    }
}
