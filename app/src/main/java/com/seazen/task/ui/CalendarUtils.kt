package com.seazen.task.ui

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

object CalendarUtils {

    fun formatHeaderDate(date: LocalDate): String {
        return try {
            val day = date.dayOfMonth
            val month = date.monthValue
            val year = date.year
            val dayOfWeek = when (date.dayOfWeek) {
                DayOfWeek.MONDAY -> "Thứ Hai"
                DayOfWeek.TUESDAY -> "Thứ Ba"
                DayOfWeek.WEDNESDAY -> "Thứ Tư"
                DayOfWeek.THURSDAY -> "Thứ Năm"
                DayOfWeek.FRIDAY -> "Thứ Sáu"
                DayOfWeek.SATURDAY -> "Thứ Bảy"
                DayOfWeek.SUNDAY -> "Chủ Nhật"
            }
            "$day Th$month, $year  $dayOfWeek"
        } catch (_: Exception) {
            "Hôm nay"
        }
    }

    fun formatMonthYear(yearMonth: YearMonth): String {
        return try {
            "Tháng ${yearMonth.monthValue}, ${yearMonth.year}"
        } catch (_: Exception) {
            "Tháng này"
        }
    }

    fun formatShortDate(date: LocalDate): String {
        return try {
            String.format(Locale.getDefault(), "%02d/%02d", date.dayOfMonth, date.monthValue)
        } catch (_: Exception) {
            "--/--"
        }
    }

    fun formatDisplayRange(startDate: String?, endDate: String?): String {
        val s = cleanDate(startDate)
        val e = cleanDate(endDate)
        return when {
            s.isNotBlank() && e.isNotBlank() && s != e -> "$s - $e"
            e.isNotBlank() -> "Hạn: $e"
            s.isNotBlank() -> "Bắt đầu: $s"
            else -> "Hôm nay"
        }
    }

    private fun cleanDate(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return try {
            val dateOnly = if (raw.contains("T")) raw.split("T")[0] else raw.trim()
            val parts = dateOnly.split("-")
            if (parts.size == 3) "${parts[2]}/${parts[1]}" else dateOnly
        } catch (_: Exception) {
            raw ?: ""
        }
    }

    fun getDaysInMonthGrid(yearMonth: YearMonth): List<LocalDate?> {
        return try {
            val firstDay = yearMonth.atDay(1)
            val daysInMonth = yearMonth.lengthOfMonth()
            val startOffset = firstDay.dayOfWeek.value - 1

            val grid = mutableListOf<LocalDate?>()
            for (i in 0 until startOffset) {
                grid.add(null)
            }
            for (day in 1..daysInMonth) {
                grid.add(yearMonth.atDay(day))
            }
            grid
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun showDatePicker(
        context: android.content.Context,
        initialDate: LocalDate = LocalDate.now(),
        onDateSelected: (LocalDate) -> Unit
    ) {
        try {
            val y = initialDate.year
            val m = initialDate.monthValue - 1
            val d = initialDate.dayOfMonth
            val picker = android.app.DatePickerDialog(
                context,
                { _, selectedYear, selectedMonth, selectedDay ->
                    val result = LocalDate.of(selectedYear, selectedMonth + 1, selectedDay)
                    onDateSelected(result)
                },
                y, m, d
            )
            picker.show()
        } catch (_: Exception) {}
    }
}
