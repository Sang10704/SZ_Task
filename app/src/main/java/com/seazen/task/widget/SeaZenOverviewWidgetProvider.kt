package com.seazen.task.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.seazen.task.R
import com.seazen.task.data.TaskRepository
import com.seazen.task.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

class SeaZenOverviewWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH_OVERVIEW -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val repository = TaskRepository(context.applicationContext)
                    repository.fetchAllFromSharePoint()
                    notifyAllWidgets(context)
                }
            }
            ACTION_OPEN_CREATE -> {
                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("open_create_task", true)
                }
                context.startActivity(mainIntent)
            }
            ACTION_OPEN_TAB -> {
                val targetTab = intent.getIntExtra("target_tab", 0)
                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("open_tab", targetTab)
                }
                context.startActivity(mainIntent)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_OVERVIEW = "com.seazen.task.ACTION_OVERVIEW_REFRESH"
        const val ACTION_OPEN_CREATE = "com.seazen.task.ACTION_OVERVIEW_OPEN_CREATE"
        const val ACTION_OPEN_TAB = "com.seazen.task.ACTION_OVERVIEW_OPEN_TAB"

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            try {
                val repository = TaskRepository(context)
                val allTasks = repository.tasks.value
                val userEmail = repository.getSavedEmail().lowercase().trim()
                val today = LocalDate.now()

                val myTasks = if (userEmail.isNotBlank()) {
                    allTasks.filter { task ->
                        val email = task.safeAssigneeEmail.lowercase().trim()
                        email.contains(userEmail) || task.safeAssigneeName.lowercase().contains("sang")
                    }
                } else allTasks

                val todayTasks = myTasks.filter { it.isActiveOn(today) }
                val doneToday = todayTasks.count { it.isCompleted }
                val overdueTasks = myTasks.filter { it.isOverdue(today) }
                val overdueCount = overdueTasks.size

                val views = RemoteViews(context.packageName, R.layout.widget_seazen_overview)

                // 1. Ngày
                views.setTextViewText(R.id.widget_overview_date, formatVietnameseDate(today))

                // 2. Thống kê
                views.setTextViewText(R.id.widget_overview_today_num, "${todayTasks.size}")
                views.setTextViewText(R.id.widget_overview_done_num, "$doneToday/${todayTasks.size}")
                views.setTextViewText(R.id.widget_overview_overdue_num, "$overdueCount")
                if (overdueCount > 0) {
                    views.setTextColor(R.id.widget_overview_overdue_num, Color.parseColor("#F87171"))
                } else {
                    views.setTextColor(R.id.widget_overview_overdue_num, Color.parseColor("#64748B"))
                }

                // 3. Nút Refresh
                val refreshIntent = Intent(context, SeaZenOverviewWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH_OVERVIEW
                }
                val refreshPendingIntent = PendingIntent.getBroadcast(
                    context, 2001, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_overview_btn_refresh, refreshPendingIntent)

                // 4. Nút Thêm việc
                val addIntent = Intent(context, SeaZenOverviewWidgetProvider::class.java).apply {
                    action = ACTION_OPEN_CREATE
                }
                val addPendingIntent = PendingIntent.getBroadcast(
                    context, 2002, addIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_overview_btn_add, addPendingIntent)

                // 5. Nút điều hướng các tab
                val openTab0 = Intent(context, SeaZenOverviewWidgetProvider::class.java).apply {
                    action = ACTION_OPEN_TAB
                    putExtra("target_tab", 0)
                }
                views.setOnClickPendingIntent(
                    R.id.widget_nav_my_tasks,
                    PendingIntent.getBroadcast(context, 2003, openTab0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                )

                val openTab2 = Intent(context, SeaZenOverviewWidgetProvider::class.java).apply {
                    action = ACTION_OPEN_TAB
                    putExtra("target_tab", 2)
                }
                views.setOnClickPendingIntent(
                    R.id.widget_nav_calendar,
                    PendingIntent.getBroadcast(context, 2004, openTab2, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                )

                val openTab1 = Intent(context, SeaZenOverviewWidgetProvider::class.java).apply {
                    action = ACTION_OPEN_TAB
                    putExtra("target_tab", 1)
                }
                views.setOnClickPendingIntent(
                    R.id.widget_nav_dept,
                    PendingIntent.getBroadcast(context, 2005, openTab1, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                )

                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (_: Exception) {}
        }

        private fun notifyAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, SeaZenOverviewWidgetProvider::class.java))
                for (id in ids) {
                    updateAppWidget(context, appWidgetManager, id)
                }
            } catch (_: Exception) {}
        }

        private fun formatVietnameseDate(date: LocalDate): String {
            val dayOfWeek = when (date.dayOfWeek) {
                DayOfWeek.MONDAY -> "Thứ Hai"
                DayOfWeek.TUESDAY -> "Thứ Ba"
                DayOfWeek.WEDNESDAY -> "Thứ Tư"
                DayOfWeek.THURSDAY -> "Thứ Năm"
                DayOfWeek.FRIDAY -> "Thứ Sáu"
                DayOfWeek.SATURDAY -> "Thứ Bảy"
                DayOfWeek.SUNDAY -> "Chủ Nhật"
            }
            return "$dayOfWeek, ${String.format(Locale.getDefault(), "%02d/%02d", date.dayOfMonth, date.monthValue)}"
        }
    }
}
