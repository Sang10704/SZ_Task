package com.seazen.task.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
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

class SeaZenWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return

        when (action) {
            ACTION_ITEM_CLICK -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
                val actionType = intent.getStringExtra(EXTRA_ACTION_TYPE)

                if (actionType == ACTION_TYPE_TOGGLE && taskId > 0L) {
                    // Đánh dấu hoàn thành trực tiếp trên Widget
                    CoroutineScope(Dispatchers.IO).launch {
                        val repository = TaskRepository(context.applicationContext)
                        repository.toggleTaskCompletion(taskId)
                        notifyAllWidgets(context)
                    }
                } else if (actionType == ACTION_TYPE_OPEN_DETAIL && taskId > 0L) {
                    // Mở app tới chi tiết công việc
                    val mainIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("open_task_id", taskId)
                    }
                    context.startActivity(mainIntent)
                }
            }

            ACTION_REFRESH_TODAY -> {
                // Đồng bộ SharePoint ngầm từ Widget
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

            ACTION_OPEN_TODAY -> {
                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("open_tab", 0)
                }
                context.startActivity(mainIntent)
            }
        }
    }

    companion object {
        const val ACTION_ITEM_CLICK = "com.seazen.task.ACTION_WIDGET_TASK_ITEM_CLICK"
        const val ACTION_REFRESH_TODAY = "com.seazen.task.ACTION_WIDGET_REFRESH_TODAY"
        const val ACTION_OPEN_CREATE = "com.seazen.task.ACTION_WIDGET_OPEN_CREATE"
        const val ACTION_OPEN_TODAY = "com.seazen.task.ACTION_WIDGET_OPEN_TODAY"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_ACTION_TYPE = "extra_action_type"
        const val ACTION_TYPE_TOGGLE = "toggle"
        const val ACTION_TYPE_OPEN_DETAIL = "open_detail"

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

                val overdueTasks = myTasks.filter { it.isOverdue(today) }
                val pendingTodayTasks = myTasks.filter { !it.isCompleted && it.isActiveOn(today) }
                val overdueCount = overdueTasks.size
                val todayCount = pendingTodayTasks.size

                val views = RemoteViews(context.packageName, R.layout.widget_seazen_task)

                // 1. Ngày header
                views.setTextViewText(R.id.widget_header_date, formatVietnameseDate(today))

                // 2. Badge đếm
                views.setTextViewText(R.id.widget_badge_today, "$todayCount việc tồn")
                if (overdueCount > 0) {
                    views.setViewVisibility(R.id.widget_badge_overdue, View.VISIBLE)
                    views.setTextViewText(R.id.widget_badge_overdue, "$overdueCount trễ hạn")
                } else {
                    views.setViewVisibility(R.id.widget_badge_overdue, View.GONE)
                }

                // 3. Adapter cho ListView
                val serviceIntent = Intent(context, SeaZenWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                }
                views.setRemoteAdapter(R.id.widget_task_list, serviceIntent)
                views.setEmptyView(R.id.widget_task_list, R.id.widget_empty_view)

                // 4. PendingIntentTemplate cho ListView
                val itemClickIntent = Intent(context, SeaZenWidgetProvider::class.java).apply {
                    action = ACTION_ITEM_CLICK
                }
                val itemClickPendingIntent = PendingIntent.getBroadcast(
                    context,
                    1001,
                    itemClickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                views.setPendingIntentTemplate(R.id.widget_task_list, itemClickPendingIntent)

                // 5. Nút Làm mới (Sync)
                val refreshIntent = Intent(context, SeaZenWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH_TODAY
                }
                val refreshPendingIntent = PendingIntent.getBroadcast(
                    context,
                    1002,
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPendingIntent)

                // 6. Nút Tạo việc mới (+)
                val addIntent = Intent(context, SeaZenWidgetProvider::class.java).apply {
                    action = ACTION_OPEN_CREATE
                }
                val addPendingIntent = PendingIntent.getBroadcast(
                    context,
                    1003,
                    addIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_btn_add, addPendingIntent)

                // 7. Nhấn vào header mở Tab 0 (Cá nhân)
                val openTodayIntent = Intent(context, SeaZenWidgetProvider::class.java).apply {
                    action = ACTION_OPEN_TODAY
                }
                val openTodayPendingIntent = PendingIntent.getBroadcast(
                    context,
                    1004,
                    openTodayIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_header_date, openTodayPendingIntent)

                appWidgetManager.updateAppWidget(appWidgetId, views)
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_task_list)
            } catch (_: Exception) {}
        }

        private fun notifyAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, SeaZenWidgetProvider::class.java))
                for (id in ids) {
                    updateAppWidget(context, appWidgetManager, id)
                }
                appWidgetManager.notifyAppWidgetViewDataChanged(ids, R.id.widget_task_list)
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
