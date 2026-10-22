package com.seazen.task.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.seazen.task.R
import com.seazen.task.data.TaskItem
import com.seazen.task.data.TaskRepository
import java.time.LocalDate

class SeaZenWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return SeaZenWidgetViewsFactory(this.applicationContext)
    }
}

class SeaZenWidgetViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var taskList: List<TaskItem> = emptyList()

    override fun onCreate() {
        loadData()
    }

    override fun onDataSetChanged() {
        loadData()
    }

    private fun loadData() {
        try {
            val repository = TaskRepository(context)
            val allTasks = repository.tasks.value
            val userEmail = repository.getSavedEmail().lowercase().trim()
            val today = LocalDate.now()

            // Lọc công việc của người dùng hiện tại
            val myTasks = if (userEmail.isNotBlank()) {
                allTasks.filter { task ->
                    val email = task.safeAssigneeEmail.lowercase().trim()
                    email.contains(userEmail) || task.safeAssigneeName.lowercase().contains("sang")
                }
            } else {
                allTasks
            }

            // Lấy danh sách việc đến hạn hôm nay hoặc trễ hạn (hoặc vừa xong trong ngày)
            val relevantTasks = myTasks.filter { task ->
                task.isOverdue(today) || task.isActiveOn(today)
            }

            // Sắp xếp: Trễ hạn chưa xong -> Khẩn cấp chưa xong -> Chưa xong -> Đã xong
            taskList = relevantTasks.sortedWith(
                compareBy<TaskItem> { it.isCompleted }
                    .thenByDescending { it.isOverdue(today) && !it.isCompleted }
                    .thenByDescending { it.safePriority.lowercase() in listOf("urgent", "khẩn cấp", "high") }
                    .thenBy { it.safeEndDate }
            )
        } catch (_: Exception) {
            taskList = emptyList()
        }
    }

    override fun onDestroy() {
        taskList = emptyList()
    }

    override fun getCount(): Int = taskList.size

    override fun getViewAt(position: Int): RemoteViews? {
        if (position !in taskList.indices) return null
        val task = taskList[position]
        val views = RemoteViews(context.packageName, R.layout.widget_task_item)
        val today = LocalDate.now()
        val isOverdue = task.isOverdue(today) && !task.isCompleted
        val isUrgent = task.safePriority.lowercase() in listOf("urgent", "khẩn cấp", "high") && !task.isCompleted

        // 1. Trạng thái Checkbox hoàn thành
        if (task.isCompleted) {
            views.setImageViewResource(R.id.widget_item_check_btn, R.drawable.ic_widget_check_on)
            views.setTextColor(R.id.widget_item_title, Color.parseColor("#64748B"))
        } else {
            views.setImageViewResource(R.id.widget_item_check_btn, R.drawable.ic_widget_check_off)
            views.setTextColor(R.id.widget_item_title, Color.parseColor("#F8FAFC"))
        }

        // 2. Nhãn cảnh báo (Tag: TRỄ / KHẨN)
        when {
            isOverdue -> {
                views.setViewVisibility(R.id.widget_item_tag, View.VISIBLE)
                views.setTextViewText(R.id.widget_item_tag, "TRỄ")
                views.setInt(R.id.widget_item_tag, "setBackgroundResource", R.drawable.widget_badge_red)
            }
            isUrgent -> {
                views.setViewVisibility(R.id.widget_item_tag, View.VISIBLE)
                views.setTextViewText(R.id.widget_item_tag, "KHẨN")
                views.setInt(R.id.widget_item_tag, "setBackgroundResource", R.drawable.widget_badge_amber)
            }
            else -> {
                views.setViewVisibility(R.id.widget_item_tag, View.GONE)
            }
        }

        // 3. Tiêu đề
        views.setTextViewText(R.id.widget_item_title, task.safeTitle)

        // 4. Dự án & Hạn hoàn thành
        val parentTitle = when {
            task.safeTaskChinhTitle.isNotBlank() -> task.safeTaskChinhTitle
            task.safePhongBan.isNotBlank() -> task.safePhongBan
            else -> "Cá nhân"
        }
        views.setTextViewText(R.id.widget_item_project, parentTitle)
        views.setTextViewText(R.id.widget_item_time, formatShortTime(task.safeEndDate))

        // 5. Fill-in intent cho sự kiện nhấn:
        // A. Nhấn vào hàng -> Mở chi tiết công việc trong app
        val rowIntent = Intent().apply {
            putExtra(SeaZenWidgetProvider.EXTRA_TASK_ID, task.id)
            putExtra(SeaZenWidgetProvider.EXTRA_ACTION_TYPE, SeaZenWidgetProvider.ACTION_TYPE_OPEN_DETAIL)
        }
        views.setOnClickFillInIntent(R.id.widget_item_row, rowIntent)

        // B. Nhấn vào Checkbox -> Đánh dấu hoàn thành
        val checkIntent = Intent().apply {
            putExtra(SeaZenWidgetProvider.EXTRA_TASK_ID, task.id)
            putExtra(SeaZenWidgetProvider.EXTRA_ACTION_TYPE, SeaZenWidgetProvider.ACTION_TYPE_TOGGLE)
        }
        views.setOnClickFillInIntent(R.id.widget_item_check_btn, checkIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long {
        return if (position in taskList.indices) taskList[position].id else position.toLong()
    }

    override fun hasStableIds(): Boolean = true

    private fun formatShortTime(raw: String?): String {
        if (raw.isNullOrBlank()) return "Hôm nay"
        return try {
            val dateOnly = if (raw.contains("T")) raw.split("T")[0] else raw.trim()
            val parts = dateOnly.split("-")
            if (parts.size == 3) "${parts[2]}/${parts[1]}" else dateOnly
        } catch (_: Exception) {
            "Hôm nay"
        }
    }
}
