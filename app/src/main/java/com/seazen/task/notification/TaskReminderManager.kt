package com.seazen.task.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.seazen.task.R
import com.seazen.task.data.TaskRepository
import com.seazen.task.ui.MainActivity
import com.seazen.task.widget.SeaZenWidgetProvider
import java.time.LocalDate
import java.util.Calendar

object TaskReminderManager {
    const val CHANNEL_ID = "seazen_task_reminders"
    const val ACTION_TASK_REMINDER = "com.seazen.task.ACTION_TASK_REMINDER"
    const val EXTRA_REMINDER_SLOT = "extra_reminder_slot"
    private const val PREF_REMINDER_ENABLED = "task_reminder_enabled"
    private const val PREF_PROMPT_SHOWN = "notification_prompt_shown_once"
    private const val PREF_LAST_DIGEST_DATE = "last_digest_notified_date"

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Nhắc việc & Cảnh báo hạn SZ Task",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Thông báo nhắc nhở công việc cần làm trong ngày và cảnh báo việc trễ hạn"
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        val systemEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!systemEnabled) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isReminderEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("seazen_task_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_REMINDER_ENABLED, true) && hasNotificationPermission(context)
    }

    fun setReminderEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("seazen_task_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREF_REMINDER_ENABLED, enabled).apply()
        if (enabled && hasNotificationPermission(context)) {
            scheduleDailyReminders(context)
        } else if (!enabled) {
            cancelDailyReminders(context)
        }
    }

    fun hasShownInitialPrompt(context: Context): Boolean {
        val prefs = context.getSharedPreferences("seazen_task_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_PROMPT_SHOWN, false)
    }

    fun markInitialPromptShown(context: Context) {
        val prefs = context.getSharedPreferences("seazen_task_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREF_PROMPT_SHOWN, true).apply()
    }

    fun openAppNotificationSettings(context: Context) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            } catch (_: Exception) {}
        }
    }

    fun scheduleDailyReminders(context: Context) {
        ensureNotificationChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        // Lich 1: 08:00 sang (Ke hoach cong viec hom nay)
        scheduleSlot(context, alarmManager, hourOfDay = 8, minute = 0, requestCode = 800, slotName = "MORNING")
        // Lich 2: 16:00 chieu (Ra soat cong viec chua xong trong ngay)
        scheduleSlot(context, alarmManager, hourOfDay = 16, minute = 0, requestCode = 1600, slotName = "AFTERNOON")
    }

    private fun scheduleSlot(
        context: Context,
        alarmManager: AlarmManager,
        hourOfDay: Int,
        minute: Int,
        requestCode: Int,
        slotName: String
    ) {
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = ACTION_TASK_REMINDER
            putExtra(EXTRA_REMINDER_SLOT, slotName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                cal.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        } catch (_: Exception) {}
    }

    fun cancelDailyReminders(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        listOf(800 to "MORNING", 1600 to "AFTERNOON").forEach { (code, slot) ->
            val intent = Intent(context, TaskReminderReceiver::class.java).apply {
                action = ACTION_TASK_REMINDER
                putExtra(EXTRA_REMINDER_SLOT, slot)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                code,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pi)
        }
    }

    fun checkAndNotifyAfterSync(context: Context) {
        if (!isReminderEnabled(context)) return
        val todayStr = LocalDate.now().toString()
        val prefs = context.getSharedPreferences("seazen_task_prefs", Context.MODE_PRIVATE)
        val lastDate = prefs.getString(PREF_LAST_DIGEST_DATE, "") ?: ""
        if (lastDate == todayStr) return

        val repo = TaskRepository.getInstance(context)
        val myEmail = repo.getSavedEmail().lowercase().trim()
        val myName = repo.getSavedUserName().lowercase().trim()
        if (myEmail.isBlank() && myName.isBlank()) return

        val today = LocalDate.now()
        val myTasks = repo.tasks.value.filter { SeaZenWidgetProvider.isMyTask(it, myEmail, myName) }
        val overdueCount = myTasks.count { it.isOverdue(today) && !it.isCompleted }
        val todayPendingCount = myTasks.count { it.isActiveOn(today) && !it.isCompleted }

        if (overdueCount > 0 || todayPendingCount > 0) {
            prefs.edit().putString(PREF_LAST_DIGEST_DATE, todayStr).apply()
            sendTaskSummaryNotification(context, forceShowEvenIfZero = false)
        }
    }

    fun sendTaskSummaryNotification(context: Context, forceShowEvenIfZero: Boolean = true): Boolean {
        if (!hasNotificationPermission(context)) return false
        ensureNotificationChannel(context)

        val repo = TaskRepository.getInstance(context)
        if (repo.tasks.value.isEmpty() && repo.taskChinhList.value.isEmpty()) {
            repo.loadCachedData()
        }

        val myEmail = repo.getSavedEmail().lowercase().trim()
        val myName = repo.getSavedUserName().lowercase().trim()
        val today = LocalDate.now()

        val myTasks = repo.tasks.value.filter {
            SeaZenWidgetProvider.isMyTask(it, myEmail, myName)
        }
        val overdueTasks = myTasks.filter { it.isOverdue(today) && !it.isCompleted }
        val todayPendingTasks = myTasks.filter { it.isActiveOn(today) && !it.isCompleted && !it.isOverdue(today) }

        if (!forceShowEvenIfZero && overdueTasks.isEmpty() && todayPendingTasks.isEmpty()) {
            return false
        }

        val title = when {
            overdueTasks.isNotEmpty() && todayPendingTasks.isNotEmpty() ->
                "SZ Task: ${todayPendingTasks.size} việc hôm nay • ${overdueTasks.size} việc trễ hạn"
            overdueTasks.isNotEmpty() ->
                "Cảnh báo: Bạn có ${overdueTasks.size} công việc đang trễ hạn"
            todayPendingTasks.isNotEmpty() ->
                "Kế hoạch hôm nay: ${todayPendingTasks.size} công việc cần hoàn thành"
            else ->
                "SZ Task: Bạn đã hoàn thành hết công việc hôm nay"
        }

        val lines = mutableListOf<String>()
        overdueTasks.take(3).forEach { t ->
            lines.add("[Trễ hạn] ${t.safeTitle}")
        }
        todayPendingTasks.take(3).forEach { t ->
            lines.add("[Hôm nay] ${t.safeTitle}")
        }
        if (lines.isEmpty()) {
            lines.add("Tất cả công việc cá nhân trong ngày đã hoàn tất.")
        }
        val summaryBody = lines.joinToString("\n")

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab", 0)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            2026,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(1001, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
