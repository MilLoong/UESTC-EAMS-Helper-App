package edu.uestc.eams.helper.data.mapper

import edu.uestc.eams.helper.data.parser.TeachingWeekEstimator
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object TimetableWeekCalendar {

    private val dateFmt = DateTimeFormatter.ofPattern("yyyy/M/d")

    fun mondayOfDisplayedWeek(
        displayWeek: Int,
        currentWeek: Int,
        today: LocalDate = LocalDate.now(),
        weekOneMonday: LocalDate? = null,
    ): LocalDate {
        weekOneMonday?.let { anchor ->
            return anchor.plusWeeks((displayWeek - 1).toLong())
        }
        val anchorMonday = TeachingWeekEstimator.teachingWeekMonday(today)
        return anchorMonday.plusWeeks((displayWeek - currentWeek).toLong())
    }

    fun dateForWeekday(weekMonday: LocalDate, weekday: Int): LocalDate =
        weekMonday.plusDays((weekday - 1).coerceIn(0, 6).toLong())

    /** 指定日期是否落在以 weekMonday 为起点的教学周内（周一至周日）。 */
    fun containsDate(weekMonday: LocalDate, date: LocalDate): Boolean {
        val sunday = weekMonday.plusDays(6)
        return !date.isBefore(weekMonday) && !date.isAfter(sunday)
    }

    fun formatHeaderDate(date: LocalDate): String = date.format(dateFmt)

    fun dayOfWeekLabel(dow: DayOfWeek): String =
        when (dow) {
            DayOfWeek.MONDAY -> "周一"
            DayOfWeek.TUESDAY -> "周二"
            DayOfWeek.WEDNESDAY -> "周三"
            DayOfWeek.THURSDAY -> "周四"
            DayOfWeek.FRIDAY -> "周五"
            DayOfWeek.SATURDAY -> "周六"
            DayOfWeek.SUNDAY -> "周日"
        }

    fun shortDayLabel(weekday: Int): String =
        when (weekday) {
            1 -> "一"
            2 -> "二"
            3 -> "三"
            4 -> "四"
            5 -> "五"
            6 -> "六"
            7 -> "日"
            else -> "$weekday"
        }
}
