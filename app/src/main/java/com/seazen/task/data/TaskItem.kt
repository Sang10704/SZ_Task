package com.seazen.task.data

import java.time.LocalDate

fun parseLocalDate(raw: String?): LocalDate? {
    if (raw.isNullOrBlank()) return null
    val trimmed = raw.trim()
    return try {
        if (trimmed.endsWith("Z", ignoreCase = true) || (trimmed.contains("T") && (trimmed.contains("+") || trimmed.contains("-", true) && trimmed.indexOf("-", 10) > 0))) {
            try {
                java.time.Instant.parse(trimmed).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            } catch (_: Exception) {
                java.time.OffsetDateTime.parse(trimmed).atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDate()
            }
        } else if (trimmed.contains("T")) {
            try {
                java.time.LocalDateTime.parse(trimmed).toLocalDate()
            } catch (_: Exception) {
                val clean = trimmed.split("T")[0]
                val parts = clean.split("-")
                if (parts.size == 3) LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt()) else null
            }
        } else {
            val parts = trimmed.split("-")
            if (parts.size == 3) {
                LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
            } else {
                LocalDate.parse(trimmed)
            }
        }
    } catch (_: Exception) {
        try {
            val clean = if (trimmed.contains("T")) trimmed.split("T")[0] else trimmed
            val parts = clean.split("-")
            if (parts.size == 3) {
                LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
            } else null
        } catch (_: Exception) {
            null
        }
    }
}

data class TaskChinhItem(
    val id: Long = 0L,
    val title: String? = "",
    val responsiblePerson: String? = "",
    val responsibleEmail: String? = "",
    val startDate: String? = "",
    val endDate: String? = "",
    val progress: Int = 0,
    val phongBan: String? = "SeaZen Tech",
    val phanLoaiTask: String? = "Phòng ban",
    val phanLoaiViec: String? = "Kế hoạch",
    val trangThai: String? = "Đang làm",
    val creatorName: String? = "",
    val creatorEmail: String? = "",
    val ccName: String? = "",
    val ccEmail: String? = "",
    val newEndDate: String? = "",
    val extReason: String? = "",
    val trangThaiDuyetHan: String? = "",
    val ghiChu: String? = ""
) {
    val safeTitle: String get() = title ?: "Không tiêu đề"
    val safeResponsiblePerson: String get() = responsiblePerson ?: ""
    val safeResponsibleEmail: String get() = responsibleEmail ?: ""
    val safeStartDate: String get() = startDate ?: ""
    val safeEndDate: String get() = endDate ?: ""
    val safePhongBan: String get() = if (phongBan.isNullOrBlank()) "SeaZen Tech" else phongBan
    val safePhanLoaiTask: String get() = if (phanLoaiTask.isNullOrBlank()) "Phòng ban" else phanLoaiTask
    val safePhanLoaiViec: String get() = if (phanLoaiViec.isNullOrBlank()) "Kế hoạch" else phanLoaiViec
    val safeTrangThai: String get() = if (trangThai.isNullOrBlank()) "Đang làm" else trangThai
    val safeProgress: Int get() = progress.coerceIn(0, 100)
    val safeCreatorName: String get() = creatorName ?: ""
    val safeCreatorEmail: String get() = creatorEmail ?: ""
    val safeCcName: String get() = ccName ?: ""
    val safeCcEmail: String get() = ccEmail ?: ""
    val safeNewEndDate: String get() = newEndDate ?: ""
    val safeExtReason: String get() = extReason ?: ""
    val safeTrangThaiDuyetHan: String get() = trangThaiDuyetHan ?: ""
    val safeGhiChu: String get() = ghiChu ?: ""

    fun isActiveOn(date: LocalDate): Boolean {
        val start = parseLocalDate(safeStartDate)
        val rawEnd = parseLocalDate(safeEndDate)
        val end = if (start != null && rawEnd != null && rawEnd.isBefore(start)) start else rawEnd

        return when {
            start != null && end != null -> !date.isBefore(start) && !date.isAfter(end)
            start != null -> !date.isBefore(start)
            end != null -> !date.isAfter(end)
            else -> false
        }
    }

    fun computedProgress(childTasks: List<TaskItem>): Int {
        if (childTasks.isEmpty()) return safeProgress
        val doneCount = childTasks.count { it.isCompleted }
        return ((doneCount.toFloat() / childTasks.size.toFloat()) * 100).toInt().coerceIn(0, 100)
    }

    fun isOverdue(today: LocalDate = LocalDate.now()): Boolean {
        if (safeProgress >= 100 || safeTrangThai.contains("Hoàn thành")) return false
        val end = parseLocalDate(safeEndDate) ?: return false
        return end.isBefore(today)
    }
}

data class TaskItem(
    val id: Long = 0L,
    val title: String? = "",
    val taskChinhId: Long? = null,
    val taskChinhTitle: String? = "",
    val isCompleted: Boolean = false,
    val assigneeName: String? = "",
    val assigneeEmail: String? = "",
    val startDate: String? = "",
    val endDate: String? = "",
    val priority: String? = "normal",
    val phongBan: String? = "",
    val ghiChu: String? = "",
    val created: String? = ""
) {
    val safeTitle: String get() = title ?: "Không tiêu đề"
    val safeTaskChinhTitle: String get() = taskChinhTitle ?: ""
    val safeAssigneeName: String get() = assigneeName ?: ""
    val safeAssigneeEmail: String get() = assigneeEmail ?: ""
    val safeStartDate: String get() = startDate ?: ""
    val safeEndDate: String get() = endDate ?: ""
    val safePriority: String get() = priority ?: "normal"
    val safePhongBan: String get() = phongBan ?: ""
    val safeGhiChu: String get() = ghiChu ?: ""

    fun isActiveOn(date: LocalDate): Boolean {
        val start = parseLocalDate(safeStartDate)
        val rawEnd = parseLocalDate(safeEndDate)
        val end = if (start != null && rawEnd != null && rawEnd.isBefore(start)) start else rawEnd

        return when {
            start != null && end != null -> !date.isBefore(start) && !date.isAfter(end)
            start != null -> date == start
            end != null -> date == end
            else -> true
        }
    }

    fun isOverdue(today: LocalDate = LocalDate.now()): Boolean {
        if (isCompleted) return false
        val end = parseLocalDate(safeEndDate) ?: parseLocalDate(safeStartDate) ?: return false
        return end.isBefore(today)
    }
}
