package com.seazen.task.data

data class AppUpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val notes: String,
    val hasUpdate: Boolean
)
