package com.duitku.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyFormatterTest {

    @Test
    fun `format rupiah dengan titik ribuan`() {
        assertEquals("Rp 1.250.000", MoneyFormatter.format(1_250_000))
        assertEquals("Rp 0", MoneyFormatter.format(0))
        assertEquals("Rp 999", MoneyFormatter.format(999))
        assertEquals("Rp 1.000", MoneyFormatter.format(1_000))
    }

    @Test
    fun `nominal negatif dan tanda plus`() {
        assertEquals("-Rp 50.000", MoneyFormatter.format(-50_000))
        assertEquals("+Rp 50.000", MoneyFormatter.format(50_000, showPlusSign = true))
        assertEquals("Rp 0", MoneyFormatter.format(0, showPlusSign = true))
    }

    @Test
    fun `tanpa simbol dan mata uang lain`() {
        assertEquals("12.500", MoneyFormatter.format(12_500, withSymbol = false))
        assertEquals("$ 1.200", MoneyFormatter.format(1_200, "USD"))
    }

    @Test
    fun `nilai ekstrem tidak crash`() {
        assertEquals(true, MoneyFormatter.format(Long.MIN_VALUE).startsWith("-Rp"))
        assertEquals(true, MoneyFormatter.format(Long.MAX_VALUE).startsWith("Rp"))
    }

    @Test
    fun `parse input pengguna`() {
        assertEquals(1_250_000L, MoneyFormatter.parse("Rp 1.250.000"))
        assertEquals(500L, MoneyFormatter.parse("500"))
        assertNull(MoneyFormatter.parse(""))
        assertNull(MoneyFormatter.parse("abc"))
        assertNull(MoneyFormatter.parse("99999999999999999999"))
    }

    @Test
    fun `format ringkas untuk grafik`() {
        assertEquals("1,2 jt", MoneyFormatter.compact(1_200_000))
        assertEquals("350 rb", MoneyFormatter.compact(350_000))
        assertEquals("2,5 M", MoneyFormatter.compact(2_500_000_000))
        assertEquals("900", MoneyFormatter.compact(900))
        assertEquals("-1 jt", MoneyFormatter.compact(-1_000_000))
    }
}
