package com.duitku.app.data.local

import androidx.room.TypeConverter

/**
 * Tag disimpan sebagai "|makan|kantor|" agar pencarian `LIKE '%|makan|%'` tidak salah cocok
 * (mis. tag "mak" tidak ikut cocok dengan "makan"). Enum ditangani langsung oleh Room.
 */
class Converters {
    @TypeConverter
    fun tagsToString(tags: List<String>?): String =
        if (tags.isNullOrEmpty()) "" else tags.joinToString(separator = "|", prefix = "|", postfix = "|")

    @TypeConverter
    fun stringToTags(value: String?): List<String> =
        value.orEmpty().split('|').filter { it.isNotBlank() }
}
