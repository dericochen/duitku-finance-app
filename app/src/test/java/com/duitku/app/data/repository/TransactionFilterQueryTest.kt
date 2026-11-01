package com.duitku.app.data.repository

import com.duitku.app.domain.model.TransactionFilter
import com.duitku.app.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionFilterQueryTest {

    @Test
    fun `filter kosong mengambil semua transaksi terbaru dulu`() {
        val built = TransactionFilterQuery.build(TransactionFilter())
        assertEquals("SELECT * FROM transactions ORDER BY occurredAt DESC, id DESC", built.sql)
        assertTrue(built.args.isEmpty())
    }

    @Test
    fun `kata kunci tidak pernah disambung ke SQL`() {
        val injection = "x'; DROP TABLE transactions; --"
        val built = TransactionFilterQuery.build(TransactionFilter(query = injection))
        assertFalse(built.sql.contains("DROP"))
        assertTrue(built.args.any { it is String && it.contains("DROP TABLE") })
        // Jumlah tanda tanya harus sama dengan jumlah argumen.
        assertEquals(built.sql.count { it == '?' }, built.args.size)
    }

    @Test
    fun `karakter LIKE di-escape`() {
        val built = TransactionFilterQuery.build(TransactionFilter(query = "50%_off"))
        assertTrue(built.args.first() == "%50\\%\\_off%")
    }

    @Test
    fun `kata kunci angka juga mencocokkan nominal`() {
        val built = TransactionFilterQuery.build(TransactionFilter(query = "50.000"))
        assertTrue(built.sql.contains("amount = ?"))
        assertTrue(built.args.contains(50_000L))
    }

    @Test
    fun `semua kriteria digabung dengan AND dan argumen cocok`() {
        val filter = TransactionFilter(
            fromMillis = 1_000, toMillis = 2_000,
            types = setOf(TransactionType.EXPENSE),
            walletIds = setOf(1, 2),
            categoryIds = setOf(5),
            tags = setOf("kantor"),
            minAmount = 10_000, maxAmount = 90_000,
        )
        val built = TransactionFilterQuery.build(filter)
        assertTrue(built.sql.contains("occurredAt >= ?"))
        assertTrue(built.sql.contains("occurredAt <= ?"))
        assertTrue(built.sql.contains("type IN (?)"))
        assertTrue(built.sql.contains("amount >= ?"))
        assertTrue(built.sql.contains("amount <= ?"))
        assertEquals(built.sql.count { it == '?' }, built.args.size)
        assertTrue(built.args.contains("%|kantor|%"))
    }

    @Test
    fun `urutan naik bila diminta`() {
        val built = TransactionFilterQuery.build(TransactionFilter(), ascending = true)
        assertTrue(built.sql.endsWith("ORDER BY occurredAt ASC, id ASC"))
    }

    @Test
    fun `jumlah filter aktif`() {
        val filter = TransactionFilter(query = "abc", types = setOf(TransactionType.INCOME), minAmount = 1)
        assertEquals(2, filter.activeCount)
        assertFalse(filter.isEmpty)
        assertTrue(TransactionFilter().isEmpty)
    }
}
