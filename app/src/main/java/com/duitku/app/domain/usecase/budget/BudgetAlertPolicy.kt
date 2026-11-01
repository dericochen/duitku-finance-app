package com.duitku.app.domain.usecase.budget

import com.duitku.app.domain.model.BudgetLine

/** Aturan kapan notifikasi anggaran dikirim: sekali per level (80% lalu 100%) dan reset bila pemakaian turun. */
object BudgetAlertPolicy {
    const val LEVEL_NONE = 0
    const val LEVEL_WARNING = 80
    const val LEVEL_EXCEEDED = 100

    fun levelOf(percent: Int): Int = when {
        percent >= 100 -> LEVEL_EXCEEDED
        percent >= BudgetLine.WARNING_PERCENT -> LEVEL_WARNING
        else -> LEVEL_NONE
    }

    /** @param notifiedLevel level terakhir yang sudah diberitahukan; @param currentLevel level pemakaian saat ini. */
    fun shouldNotify(notifiedLevel: Int, currentLevel: Int): Boolean = currentLevel > notifiedLevel
}
