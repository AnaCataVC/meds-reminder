package com.medsreminder.data.local.converter

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Type converters for Room to handle java.time types using ISO-8601 string representation.
 */
class RoomConverters {

    private val timeFormatter = DateTimeFormatter.ISO_LOCAL_TIME
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    @TypeConverter
    fun fromLocalTime(time: LocalTime?): String? = time?.format(timeFormatter)

    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? = value?.let { LocalTime.parse(it, timeFormatter) }

    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? = date?.format(dateFormatter)

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it, dateFormatter) }
}
