package com.seazen.task.data

data class SeaZenUser(
    val id: Long = 0L,
    val name: String = "",
    val email: String = "",
    val chucVu: String = "",
    val phongBan: String = ""
) {
    val displayName: String get() = (if (name.isNotBlank()) name else email).uppercase()

    val initials: String
        get() {
            val parts = name.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
            return if (parts.size >= 2) {
                "${parts[parts.size - 2].firstOrNull()?.uppercaseChar() ?: ""}${parts.last().firstOrNull()?.uppercaseChar() ?: ""}"
            } else {
                parts.firstOrNull()?.take(2)?.uppercase() ?: "SZ"
            }
        }
}
