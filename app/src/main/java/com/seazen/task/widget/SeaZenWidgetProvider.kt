package com.seazen.task.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import com.seazen.task.R
import com.seazen.task.data.TaskRepository
import com.seazen.task.data.parseLocalDate
import com.seazen.task.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

class SeaZenWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        val prefs = context.getSharedPreferences("seazen_task_prefs", Context.MODE_PRIVATE)

        when (action) {
            ACTION_ITEM_CLICK -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
                val actionType = intent.getStringExtra(EXTRA_ACTION_TYPE)
                val isMine = intent.getBooleanExtra(EXTRA_IS_MINE, true)
                val assignee = intent.getStringExtra(EXTRA_ASSIGNEE) ?: "nhân sự khác"

                if (actionType == ACTION_TYPE_LOCKED || !isMine) {
                    // Task của người khác: Chế độ chỉ xem, khóa sửa xóa
                    Toast.makeText(
                        context,
                        "Chỉ xem: Công việc của $assignee (Bạn không có quyền sửa/xóa)",
                        Toast.LENGTH_SHORT
                    ).show()
                } else if (actionType == ACTION_TYPE_TOGGLE && taskId > 0L) {
                    // Task của mình: Đánh dấu hoàn thành trực tiếp trên Widget
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

            ACTION_SELECT_DATE -> {
                val dateStr = intent.getStringExtra(EXTRA_SELECTED_DATE)
                if (!dateStr.isNullOrBlank()) {
                    prefs.edit().putString("widget_selected_date", dateStr).apply()
                    notifyAllWidgets(context)
                }
            }

            ACTION_CHANGE_WEEK -> {
                val delta = intent.getIntExtra(EXTRA_OFFSET_DELTA, 0)
                val current = prefs.getInt("widget_week_offset", 0)
                prefs.edit().putInt("widget_week_offset", current + delta).apply()
                notifyAllWidgets(context)
            }

            ACTION_RESET_TODAY -> {
                prefs.edit()
                    .putString("widget_selected_date", LocalDate.now().toString())
                    .putInt("widget_week_offset", 0)
                    .apply()
                notifyAllWidgets(context)
            }

            ACTION_SET_SCOPE -> {
                val newScope = intent.getStringExtra(EXTRA_SCOPE) ?: "PERSONAL"
                prefs.edit().putString("widget_scope", newScope).apply()
                notifyAllWidgets(context)
            }

            ACTION_REFRESH_TODAY -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val repository = TaskRepository(context.applicationContext)
                    repository.fetchAllFromSharePoint()
                    notifyAllWidgets(context)
                }
            }

            ACTION_OPEN_CREATE -> {
                val selectedDateStr = prefs.getString("widget_selected_date", LocalDate.now().toString())
                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("open_create_task", true)
                    putExtra("target_create_date", selectedDateStr)
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
        const val ACTION_ITEM_CLICK = "com.seazen.task.ACTION_WIDGET_TASK_ITEM_CLICK"
        const val ACTION_SELECT_DATE = "com.seazen.task.ACTION_WIDGET_SELECT_DATE"
        const val ACTION_CHANGE_WEEK = "com.seazen.task.ACTION_WIDGET_CHANGE_WEEK"
        const val ACTION_RESET_TODAY = "com.seazen.task.ACTION_WIDGET_RESET_TODAY"
        const val ACTION_REFRESH_TODAY = "com.seazen.task.ACTION_WIDGET_REFRESH_TODAY"
        const val ACTION_SET_SCOPE = "com.seazen.task.ACTION_WIDGET_SET_SCOPE"
        const val ACTION_OPEN_CREATE = "com.seazen.task.ACTION_WIDGET_OPEN_CREATE"
        const val ACTION_OPEN_TAB = "com.seazen.task.ACTION_WIDGET_OPEN_TAB"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_ACTION_TYPE = "extra_action_type"
        const val EXTRA_IS_MINE = "extra_is_mine"
        const val EXTRA_ASSIGNEE = "extra_assignee"
        const val EXTRA_SELECTED_DATE = "extra_selected_date"
        const val EXTRA_OFFSET_DELTA = "extra_offset_delta"
        const val EXTRA_SCOPE = "extra_scope"

        const val ACTION_TYPE_TOGGLE = "toggle"
        const val ACTION_TYPE_LOCKED = "locked"
        const val ACTION_TYPE_OPEN_DETAIL = "open_detail"

        fun isMyTask(
            task: com.seazen.task.data.TaskItem,
            currentUserEmail: String,
            currentUserName: String = ""
        ): Boolean {
            val myEmail = currentUserEmail.trim().lowercase().ifBlank { "sang.nguyen@seazen.com.vn" }
            val myName = currentUserName.trim().lowercase()
            val taskEmail = task.safeAssigneeEmail.trim().lowercase()
            val taskName = task.safeAssigneeName.trim().lowercase()

            // 1. So khớp Email nếu task có email
            if (taskEmail.isNotBlank()) {
                return taskEmail == myEmail || taskEmail.contains(myEmail) || myEmail.contains(taskEmail)
            }

            // 2. Nếu task không có email, so khớp Họ tên
            if (taskName.isNotBlank()) {
                if (myName.isNotBlank() && (taskName == myName || taskName.contains(myName) || myName.contains(taskName))) {
                    return true
                }
                // Nhận diện nhân sự Sang
                if (taskName.contains("sang")) {
                    return true
                }
            }

            return false
        }

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            try {
                val repository = TaskRepository(context)
                val prefs = context.getSharedPreferences("seazen_task_prefs", Context.MODE_PRIVATE)
                val allTasks = repository.tasks.value
                val currentUserEmail = repository.getSavedEmail().lowercase().trim()
                val currentUserName = repository.getSavedUserName().lowercase().trim()

                // Scope: Mặc định là PERSONAL (Cá nhân)
                val currentScope = prefs.getString("widget_scope", "PERSONAL") ?: "PERSONAL"
                val isPersonal = (currentScope == "PERSONAL")

                // 1. Xác định ngày được chọn và tuần hiển thị
                val savedDateStr = prefs.getString("widget_selected_date", null)
                val selectedDate = parseLocalDate(savedDateStr) ?: LocalDate.now()
                val weekOffset = prefs.getInt("widget_week_offset", 0)

                val baseDate = LocalDate.now().plusWeeks(weekOffset.toLong())
                val monday = baseDate.with(DayOfWeek.MONDAY)
                val daysOfWeek = (0..6).map { monday.plusDays(it.toLong()) }

                val views = RemoteViews(context.packageName, R.layout.widget_seazen_task)

                // 2. Tiêu đề và Tháng
                views.setTextViewText(R.id.widget_header_subtitle, "Tháng ${selectedDate.monthValue}, ${selectedDate.year}")
                val greeting = if (selectedDate == LocalDate.now()) {
                    "Kế hoạch hôm nay"
                } else {
                    "Ngày ${selectedDate.dayOfMonth}/${selectedDate.monthValue}"
                }
                views.setTextViewText(R.id.widget_header_greeting, greeting)

                // 3. Cập nhật 7 ngày trên thanh lịch (Calendar Strip)
                val dayNameIds = intArrayOf(
                    R.id.widget_day_name_0, R.id.widget_day_name_1, R.id.widget_day_name_2,
                    R.id.widget_day_name_3, R.id.widget_day_name_4, R.id.widget_day_name_5, R.id.widget_day_name_6
                )
                val dayNumIds = intArrayOf(
                    R.id.widget_day_num_0, R.id.widget_day_num_1, R.id.widget_day_num_2,
                    R.id.widget_day_num_3, R.id.widget_day_num_4, R.id.widget_day_num_5, R.id.widget_day_num_6
                )
                val dayDotIds = intArrayOf(
                    R.id.widget_day_dot_0, R.id.widget_day_dot_1, R.id.widget_day_dot_2,
                    R.id.widget_day_dot_3, R.id.widget_day_dot_4, R.id.widget_day_dot_5, R.id.widget_day_dot_6
                )
                val dayNames = arrayOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")

                for (i in 0..6) {
                    val date = daysOfWeek[i]
                    views.setTextViewText(dayNameIds[i], dayNames[i])
                    views.setTextViewText(dayNumIds[i], "${date.dayOfMonth}")

                    // Định dạng highlight cho ngày được chọn và hôm nay
                    val isSelected = date == selectedDate
                    val isToday = date == LocalDate.now()

                    when {
                        isSelected -> {
                            views.setInt(dayNumIds[i], "setBackgroundResource", R.drawable.widget_day_selected_bg)
                            views.setTextColor(dayNumIds[i], Color.WHITE)
                        }
                        isToday -> {
                            views.setInt(dayNumIds[i], "setBackgroundResource", R.drawable.widget_day_today_bg)
                            views.setTextColor(dayNumIds[i], Color.parseColor("#5B4DF6"))
                        }
                        else -> {
                            views.setInt(dayNumIds[i], "setBackgroundResource", android.R.color.transparent)
                            views.setTextColor(dayNumIds[i], Color.parseColor("#0F172A"))
                        }
                    }

                    // Chấm báo hiệu có công việc trong ngày theo phạm vi lọc
                    val hasTasks = allTasks.any { task ->
                        task.isActiveOn(date) && (!isPersonal || isMyTask(task, currentUserEmail, currentUserName))
                    }
                    views.setViewVisibility(dayDotIds[i], if (hasTasks) View.VISIBLE else View.INVISIBLE)

                    // Sự kiện nhấn vào ngày để chọn
                    val selectIntent = Intent(context, SeaZenWidgetProvider::class.java).apply {
                        action = ACTION_SELECT_DATE
                        putExtra(EXTRA_SELECTED_DATE, date.toString())
                    }
                    val selectPending = PendingIntent.getBroadcast(
                        context,
                        1100 + i,
                        selectIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(dayNumIds[i], selectPending)
                }

                // 4. Các nút điều hướng tuần & đồng bộ
                val prevIntent = Intent(context, SeaZenWidgetProvider::class.java).apply {
                    action = ACTION_CHANGE_WEEK
                    putExtra(EXTRA_OFFSET_DELTA, -1)
                }
                views.setOnClickPendingIntent(
                    R.id.widget_btn_prev_week,
                    PendingIntent.getBroadcast(context, 1201, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                )

                val nextIntent = Intent(context, SeaZenWidgetProvider::class.java).apply {
                    action = ACTION_CHANGE_WEEK
                    putExtra(EXTRA_OFFSET_DELTA, 1)
                }
                views.setOnClickPendingIntent(
                    R.id.widget_btn_next_week,
                    PendingIntent.getBroadcast(context, 1202, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                )

                val refreshIntent = Intent(context, SeaZenWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH_TODAY
                }
                views.setOnClickPendingIntent(
                    R.id.widget_btn_refresh,
                    PendingIntent.getBroadcast(context, 1203, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                )

                // 5. Cấu hình Adapter danh sách công việc
                val serviceIntent = Intent(context, SeaZenWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                }
                views.setRemoteAdapter(R.id.widget_task_list, serviceIntent)
                views.setEmptyView(R.id.widget_task_list, R.id.widget_empty_view)

                // Template sự kiện danh sách
                val itemClickIntent = Intent(context, SeaZenWidgetProvider::class.java).apply {
                    action = ACTION_ITEM_CLICK
                }
                val itemClickPending = PendingIntent.getBroadcast(
                    context,
                    1204,
                    itemClickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                views.setPendingIntentTemplate(R.id.widget_task_list, itemClickPending)

                // 6. Thanh bộ lọc phạm vi (Cá nhân / Tất cả) & Nút Thêm mới (+)
                if (isPersonal) {
                    views.setInt(R.id.widget_btn_scope_personal, "setBackgroundResource", R.drawable.widget_pill_active_segment)
                    views.setTextColor(R.id.widget_btn_scope_personal, Color.parseColor("#5B4DF6"))
                    views.setInt(R.id.widget_btn_scope_all, "setBackgroundResource", R.drawable.widget_pill_inactive_segment)
                    views.setTextColor(R.id.widget_btn_scope_all, Color.parseColor("#64748B"))
                } else {
                    views.setInt(R.id.widget_btn_scope_personal, "setBackgroundResource", R.drawable.widget_pill_inactive_segment)
                    views.setTextColor(R.id.widget_btn_scope_personal, Color.parseColor("#64748B"))
                    views.setInt(R.id.widget_btn_scope_all, "setBackgroundResource", R.drawable.widget_pill_active_segment)
                    views.setTextColor(R.id.widget_btn_scope_all, Color.parseColor("#5B4DF6"))
                }

                val personalIntent = Intent(context, SeaZenWidgetProvider::class.java).apply {
                    action = ACTION_SET_SCOPE
                    putExtra(EXTRA_SCOPE, "PERSONAL")
                }
                views.setOnClickPendingIntent(
                    R.id.widget_btn_scope_personal,
                    PendingIntent.getBroadcast(context, 1205, personalIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                )

                val allIntent = Intent(context, SeaZenWidgetProvider::class.java).apply {
                    action = ACTION_SET_SCOPE
                    putExtra(EXTRA_SCOPE, "ALL")
                }
                views.setOnClickPendingIntent(
                    R.id.widget_btn_scope_all,
                    PendingIntent.getBroadcast(context, 1206, allIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                )

                val addIntent = Intent(context, SeaZenWidgetProvider::class.java).apply {
                    action = ACTION_OPEN_CREATE
                }
                views.setOnClickPendingIntent(
                    R.id.widget_btn_add,
                    PendingIntent.getBroadcast(context, 1208, addIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                )

                appWidgetManager.updateAppWidget(appWidgetId, views)
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_task_list)
            } catch (_: Exception) {}
        }

        fun notifyAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, SeaZenWidgetProvider::class.java))
                for (id in ids) {
                    updateAppWidget(context, appWidgetManager, id)
                }
                appWidgetManager.notifyAppWidgetViewDataChanged(ids, R.id.widget_task_list)
            } catch (_: Exception) {}
        }
    }
}
