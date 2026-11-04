package com.seazen.task.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.seazen.task.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (TaskReminderManager.isReminderEnabled(context)) {
                    TaskReminderManager.scheduleDailyReminders(context)
                }
            }

            TaskReminderManager.ACTION_TASK_REMINDER -> {
                if (!TaskReminderManager.isReminderEnabled(context)) return
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val repo = TaskRepository.getInstance(context)
                        if (repo.isConfigured() && repo.isNetworkAvailable()) {
                            repo.fetchAllFromSharePoint()
                        } else {
                            repo.loadCachedData()
                        }
                        TaskReminderManager.sendTaskSummaryNotification(
                            context = context,
                            forceShowEvenIfZero = false
                        )
                    } catch (_: Exception) {
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
