package com.duitku.app.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Format tanggal & jam sesuai locale Indonesia (id-ID). */
object DateFormats {
    val localeId: Locale = Locale.forLanguageTag("id-ID")

    private val dateShort = DateTimeFormatter.ofPattern("d MMM yyyy", localeId)
    private val dateLong = DateTimeFormatter.ofPattern("d MMMM yyyy", localeId)
    private val dayHeader = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", localeId)
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", localeId)
    private val time = DateTimeFormatter.ofPattern("HH:mm", localeId)
    private val dateTime = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", localeId)
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", localeId)
    private val monthShort = DateTimeFormatter.ofPattern("MMM", localeId)
    private val monthShortYear = DateTimeFormatter.ofPattern("MMM yy", localeId)
    private val dayShortName = DateTimeFormatter.ofPattern("EEE", localeId)
    private val fileStamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)

    fun zone(): ZoneId = ZoneId.systemDefault()

    fun date(date: LocalDate): String = dateShort.format(date)
    fun dateLong(date: LocalDate): String = dateLong.format(date)
    fun dayHeader(date: LocalDate): String = dayHeader.format(date)
    fun dayMonth(date: LocalDate): String = dayMonth.format(date)
    fun dayName(date: LocalDate): String = dayShortName.format(date)
    fun monthYear(ym: YearMonth): String = monthYear.format(ym.atDay(1))
    fun monthShort(ym: YearMonth): String = monthShort.format(ym.atDay(1))
    fun monthShortYear(ym: YearMonth): String = monthShortYear.format(ym.atDay(1))

    fun date(epochMillis: Long): String = dateShort.format(epochMillis.toLocalDateTime())
    fun time(epochMillis: Long): String = time.format(epochMillis.toLocalDateTime())
    fun time(value: java.time.LocalTime): String = time.format(value)
    fun dateTime(epochMillis: Long): String = dateTime.format(epochMillis.toLocalDateTime())

    /** Nama file aman: 20261101-083015. */
    fun fileStamp(epochMillis: Long = System.currentTimeMillis()): String =
        fileStamp.format(epochMillis.toLocalDateTime())

    /** Nama hari Indonesia (Senin..Minggu) untuk analisis "hari paling boros". */
    fun fullDayName(date: LocalDate): String = DateTimeFormatter.ofPattern("EEEE", localeId).format(date)
}

fun Long.toLocalDateTime(zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(this), zone)

fun Long.toLocalDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate = toLocalDateTime(zone).toLocalDate()

fun LocalDateTime.toEpochMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
    atZone(zone).toInstant().toEpochMilli()

fun LocalDate.startOfDayMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
    atStartOfDay(zone).toInstant().toEpochMilli()

/** Milidetik terakhir pada hari tersebut (inklusif), cocok untuk BETWEEN di SQL. */
fun LocalDate.endOfDayMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
    plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
