package com.duitku.app.domain.usecase.planning

import com.duitku.app.core.util.toLocalDate
import com.duitku.app.domain.model.DebtState
import com.duitku.app.domain.model.DebtWithPaid
import com.duitku.app.domain.model.GoalContribution
import com.duitku.app.domain.model.GoalEstimate
import com.duitku.app.domain.model.RecurrenceFrequency
import com.duitku.app.domain.model.SavingGoal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Jadwal transaksi berulang. Semua kemunculan dihitung dari tanggal mulai agar tanggal 31 tidak "melorot". */
object RecurrenceCalculator {
    const val MAX_CATCH_UP = 400

    data class Occurrence(val index: Long, val at: LocalDateTime)

    fun occurrence(start: LocalDateTime, frequency: RecurrenceFrequency, interval: Int, index: Long): LocalDateTime {
        val step = index * interval.coerceAtLeast(1)
        return when (frequency) {
            RecurrenceFrequency.DAILY -> start.plusDays(step)
            RecurrenceFrequency.WEEKLY -> start.plusWeeks(step)
            RecurrenceFrequency.MONTHLY -> start.plusMonths(step)
            RecurrenceFrequency.YEARLY -> start.plusYears(step)
        }
    }

    /** Indeks kemunculan pertama yang waktunya >= [t]. */
    fun indexAtOrAfter(start: LocalDateTime, frequency: RecurrenceFrequency, interval: Int, t: LocalDateTime): Long {
        if (!t.isAfter(start)) return 0
        val unitsBetween = when (frequency) {
            RecurrenceFrequency.DAILY -> ChronoUnit.DAYS.between(start, t)
            RecurrenceFrequency.WEEKLY -> ChronoUnit.WEEKS.between(start, t)
            RecurrenceFrequency.MONTHLY -> ChronoUnit.MONTHS.between(start, t)
            RecurrenceFrequency.YEARLY -> ChronoUnit.YEARS.between(start, t)
        }
        var k = (unitsBetween / interval.coerceAtLeast(1) - 1).coerceAtLeast(0)
        while (occurrence(start, frequency, interval, k).isBefore(t)) k++
        return k
    }

    /** Kemunculan pertama yang >= [t]. */
    fun firstAtOrAfter(start: LocalDateTime, frequency: RecurrenceFrequency, interval: Int, t: LocalDateTime): Occurrence {
        val k = indexAtOrAfter(start, frequency, interval, t)
        return Occurrence(k, occurrence(start, frequency, interval, k))
    }

    /**
     * Kemunculan yang sudah jatuh tempo (dari [nextDue] sampai [now], tidak melewati [end]).
     * Dibatasi [MAX_CATCH_UP] agar perangkat yang lama mati tidak membuat ribuan transaksi sekaligus.
     */
    fun dueOccurrences(
        start: LocalDateTime,
        frequency: RecurrenceFrequency,
        interval: Int,
        nextDue: LocalDateTime,
        now: LocalDateTime,
        end: LocalDateTime?,
    ): List<Occurrence> {
        val result = ArrayList<Occurrence>()
        var k = indexAtOrAfter(start, frequency, interval, nextDue)
        while (result.size < MAX_CATCH_UP) {
            val at = occurrence(start, frequency, interval, k)
            if (at.isAfter(now) || (end != null && at.isAfter(end))) break
            result += Occurrence(k, at)
            k++
        }
        return result
    }
}

object GoalCalculator {
    const val RATE_WINDOW_DAYS = 90L

    /**
     * @param contributions seluruh setoran/penarikan target ini.
     */
    fun estimate(goal: SavingGoal, contributions: List<GoalContribution>, today: LocalDate = LocalDate.now()): GoalEstimate {
        val remaining = goal.remaining
        val windowStart = today.minusDays(RATE_WINDOW_DAYS)
        val createdDay = goal.createdAt.takeIf { it > 0 }?.toLocalDate() ?: today
        val effectiveStart = maxOf(windowStart, createdDay)
        val days = ChronoUnit.DAYS.between(effectiveStart, today).coerceAtLeast(1)
        val recent = contributions.filter { it.contributedAt.toLocalDate() >= effectiveStart }.sumOf { it.amount }
        val perDay = recent.toDouble() / days

        val estimated = when {
            remaining == 0L -> today
            perDay <= 0.0 -> null
            else -> today.plusDays(Math.ceil(remaining / perDay).toLong().coerceAtMost(36_500))
        }

        val deadline = goal.deadline?.toLocalDate()
        val needed = if (deadline == null || remaining == 0L || !deadline.isAfter(today)) {
            null
        } else {
            val months = (ChronoUnit.DAYS.between(today, deadline) / 30.0).coerceAtLeast(1.0)
            Math.ceil(remaining / months).toLong()
        }
        val onTrack = when {
            remaining == 0L -> true
            deadline == null || estimated == null -> null
            else -> !estimated.isAfter(deadline)
        }
        return GoalEstimate(estimated, needed, onTrack)
    }
}

object DebtCalculator {
    const val SOON_DAYS = 7L

    fun state(item: DebtWithPaid, today: LocalDate = LocalDate.now()): DebtState {
        if (item.debt.isPaidOff || item.remaining == 0L) return DebtState.PAID
        val due = item.debt.dueDate?.toLocalDate() ?: return DebtState.ACTIVE
        return when {
            due.isBefore(today) -> DebtState.OVERDUE
            !due.isAfter(today.plusDays(SOON_DAYS)) -> DebtState.DUE_SOON
            else -> DebtState.ACTIVE
        }
    }
}

/** Kapan pengingat jatuh tempo dikirim: H-3, H-1, hari H, lalu tiap 3 hari saat terlambat. */
object DebtReminderPolicy {
    const val OVERDUE_REPEAT_DAYS = 3L

    /** Level pengingat: 3, 1, 0 (hari H), -1 (terlambat), atau null bila belum waktunya. */
    fun levelFor(daysUntilDue: Long): Int? = when {
        daysUntilDue == 3L -> 3
        daysUntilDue == 1L -> 1
        daysUntilDue == 0L -> 0
        daysUntilDue < 0 -> -1
        else -> null
    }

    /**
     * @param lastLevel level terakhir yang diberitahukan (null = belum pernah).
     * @param lastNotifiedDay hari (epochDay) pemberitahuan terakhir.
     */
    fun shouldNotify(level: Int?, lastLevel: Int?, lastNotifiedDay: Long?, todayEpochDay: Long): Boolean {
        if (level == null) return false
        if (level == -1) {
            return lastLevel != -1 || lastNotifiedDay == null || todayEpochDay - lastNotifiedDay >= OVERDUE_REPEAT_DAYS
        }
        return level != lastLevel
    }
}

object RecurringMath {
    /** Perkiraan nilai per bulan: harian x30, mingguan x52/12, bulanan x1, tahunan /12; dibagi interval. */
    fun monthlyEquivalent(amount: Long, frequency: RecurrenceFrequency, intervalCount: Int): Long {
        val perMonth = when (frequency) {
            RecurrenceFrequency.DAILY -> 30.0
            RecurrenceFrequency.WEEKLY -> 52.0 / 12
            RecurrenceFrequency.MONTHLY -> 1.0
            RecurrenceFrequency.YEARLY -> 1.0 / 12
        }
        return Math.round(amount * perMonth / intervalCount.coerceAtLeast(1))
    }
}