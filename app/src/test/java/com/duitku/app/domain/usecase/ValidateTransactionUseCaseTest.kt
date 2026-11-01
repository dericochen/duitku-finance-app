package com.duitku.app.domain.usecase

import com.duitku.app.domain.model.Transaction
import com.duitku.app.domain.model.TransactionError
import com.duitku.app.domain.model.TransactionRules
import com.duitku.app.domain.model.TransactionType
import com.duitku.app.domain.usecase.transaction.ValidateTransactionUseCase
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidateTransactionUseCaseTest {

    private val validate = ValidateTransactionUseCase()
    private val now = LocalDateTime.of(2026, 11, 1, 10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun expense(
        amount: Long = 50_000,
        walletId: Long = 1,
        categoryId: Long? = 3,
        note: String = "",
        tags: List<String> = emptyList(),
        at: Long = now,
    ) = Transaction(
        type = TransactionType.EXPENSE, amount = amount, walletId = walletId,
        categoryId = categoryId, note = note, tags = tags, occurredAt = at,
    )

    @Test
    fun `transaksi pengeluaran valid tidak punya error`() {
        assertTrue(validate(expense()).isEmpty())
    }

    @Test
    fun `nominal kosong`() {
        assertEquals(listOf(TransactionError.AMOUNT_EMPTY), validate(expense(amount = 0), amountEntered = false))
    }

    @Test
    fun `nominal nol atau negatif ditolak`() {
        assertTrue(TransactionError.AMOUNT_NOT_POSITIVE in validate(expense(amount = 0)))
        assertTrue(TransactionError.AMOUNT_NOT_POSITIVE in validate(expense(amount = -5)))
    }

    @Test
    fun `nominal terlalu besar ditolak`() {
        assertTrue(TransactionError.AMOUNT_TOO_LARGE in validate(expense(amount = TransactionRules.MAX_AMOUNT + 1)))
        assertTrue(validate(expense(amount = TransactionRules.MAX_AMOUNT)).isEmpty())
    }

    @Test
    fun `dompet dan kategori wajib untuk pemasukan dan pengeluaran`() {
        val errors = validate(expense(walletId = 0, categoryId = null))
        assertTrue(TransactionError.WALLET_MISSING in errors)
        assertTrue(TransactionError.CATEGORY_MISSING in errors)
    }

    @Test
    fun `transfer butuh dompet tujuan berbeda dan tidak butuh kategori`() {
        val base = Transaction(type = TransactionType.TRANSFER, amount = 100_000, walletId = 1, occurredAt = now)
        assertEquals(listOf(TransactionError.TO_WALLET_MISSING), validate(base))
        assertEquals(listOf(TransactionError.SAME_WALLET), validate(base.copy(toWalletId = 1)))
        assertTrue(validate(base.copy(toWalletId = 2)).isEmpty())
    }

    @Test
    fun `catatan dan tag dibatasi`() {
        assertTrue(TransactionError.NOTE_TOO_LONG in validate(expense(note = "x".repeat(201))))
        assertTrue(TransactionError.TOO_MANY_TAGS in validate(expense(tags = List(11) { "t$it" })))
        assertTrue(TransactionError.TAG_TOO_LONG in validate(expense(tags = listOf("a".repeat(25)))))
    }

    @Test
    fun `tahun di luar rentang ditolak`() {
        val year1990 = LocalDateTime.of(1990, 1, 1, 0, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertTrue(TransactionError.DATE_OUT_OF_RANGE in validate(expense(at = year1990)))
    }

    @Test
    fun `normalisasi tag`() {
        assertEquals(listOf("makan", "kantor"), TransactionRules.normalizeTags(listOf(" #Makan ", "KANTOR", "makan", "  ", "|")))
    }

    @Test
    fun `efek transaksi terhadap saldo dompet`() {
        val transfer = Transaction(type = TransactionType.TRANSFER, amount = 100, walletId = 1, toWalletId = 2, occurredAt = now)
        assertEquals(-100, transfer.effectOn(1))
        assertEquals(100, transfer.effectOn(2))
        assertEquals(0, transfer.effectOn(3))
        assertEquals(-50_000, expense().effectOn(1))
        assertEquals(0, expense().effectOn(2))
    }
}
