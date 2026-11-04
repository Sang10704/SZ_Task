package com.seazen.task.data

data class SpAttachmentItem(
    val fileName: String,
    val serverRelativeUrl: String
) {
    val isImage: Boolean
        get() {
            val lower = fileName.lowercase()
            return lower.endsWith(".jpg") ||
                   lower.endsWith(".jpeg") ||
                   lower.endsWith(".png") ||
                   lower.endsWith(".webp") ||
                   lower.endsWith(".gif") ||
                   lower.endsWith(".bmp")
        }
}
