package com.seazen.task.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.seazen.task.R
import com.seazen.task.data.TaskItem
import com.seazen.task.data.TaskRepository
import com.seazen.task.data.parseLocalDate
import java.time.LocalDate

class SeaZenWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return SeaZenWidgetViewsFactory(this.applicationContext)
    }
}

class SeaZenWidgetViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var taskList: List<TaskItem> = emptyList()
    private var currentUserEmail: String = ""
    private var activeDate: LocalDate = LocalDate.now()

    override fun onCreate() {
        loadData()
    }

    override fun onDataSetChanged() {
        loadData()
    }

    private fun loadData() {
        try {
            val repository = TaskRepository(context)
            val prefs = context.getSharedPreferences("seazen_task_prefs", Context.MODE_PRIVATE)
            val allTasks = repository.tasks.value
            currentUserEmail = repository.getSavedEmail().lowercase().trim()

            // Lấy ngày đang được chọn trên lịch widget (mặc định hôm nay)
            val dateStr = prefs.getString("widget_selected_date", null)
            activeDate = parseLocalDate(dateStr) ?: LocalDate.now()

            // Lấy tất cả công việc (của cả mình và người khác) hoạt động trong ngày này
            val tasksOnDate = allTasks.filter { it.isActiveOn(activeDate) }

            // Sắp xếp: Công việc của bản thân đưa lên trước, sau đó là của người khác
            taskList = tasksOnDate.sortedWith(
                compareByDescending<TaskItem> { isMyTask(it) }
                    .thenBy { it.isCompleted }
                    .thenByDescending { it.isOverdue(LocalDate.now()) && !it.isCompleted }
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
        val isMine = isMyTask(task)
        val today = LocalDate.now()
        val isOverdue = task.isOverdue(today) && !task.isCompleted
        val isUrgent = task.safePriority.lowercase() in listOf("urgent", "khẩn cấp", "high") && !task.isCompleted

        // 1. Phân quyền và Trạng thái bên trái
        if (isMine) {
            // Việc của người dùng: Hiện checkbox tương tác
            views.setViewVisibility(R.id.widget_item_check_btn, View.VISIBLE)
            views.setViewVisibility(R.id.widget_item_lock_icon, View.GONE)

            if (task.isCompleted) {
                views.setImageViewResource(R.id.widget_item_check_btn, R.drawable.ic_widget_check_on)
                views.setTextColor(R.id.widget_item_title, Color.parseColor("#94A3B8"))
            } else {
                views.setImageViewResource(R.id.widget_item_check_btn, R.drawable.ic_widget_check_off)
                views.setTextColor(R.id.widget_item_title, Color.parseColor("#0F172A"))
            }

            // Gán intent cho nút Checkbox để tick xong việc
            val checkIntent = Intent().apply {
                putExtra(SeaZenWidgetProvider.EXTRA_TASK_ID, task.id)
                putExtra(SeaZenWidgetProvider.EXTRA_ACTION_TYPE, SeaZenWidgetProvider.ACTION_TYPE_TOGGLE)
                putExtra(SeaZenWidgetProvider.EXTRA_IS_MINE, true)
            }
            views.setOnClickFillInIntent(R.id.widget_item_check_btn, checkIntent)
        } else {
            // Việc của người khác: Khóa thao tác (Disable/Read-only)
            views.setViewVisibility(R.id.widget_item_check_btn, View.GONE)
            views.setViewVisibility(R.id.widget_item_lock_icon, View.VISIBLE)
            views.setTextColor(R.id.widget_item_title, Color.parseColor("#334155"))

            // Nhấn vào biểu tượng khóa hiển thị thông báo chỉ xem
            val lockIntent = Intent().apply {
                putExtra(SeaZenWidgetProvider.EXTRA_TASK_ID, task.id)
                putExtra(SeaZenWidgetProvider.EXTRA_ACTION_TYPE, SeaZenWidgetProvider.ACTION_TYPE_LOCKED)
                putExtra(SeaZenWidgetProvider.EXTRA_ASSIGNEE, task.safeAssigneeName)
            }
            views.setOnClickFillInIntent(R.id.widget_item_lock_icon, lockIntent)
        }

        // 2. Tiêu đề công việc
        views.setTextViewText(R.id.widget_item_title, task.safeTitle)

        // 3. Phụ đề: Giờ • Dự án • Tên người thực hiện
        val timeDisplay = formatShortTime(task.safeEndDate)
        val projDisplay = if (task.safeTaskChinhTitle.isNotBlank()) task.safeTaskChinhTitle else "Kế hoạch"
        val assigneeDisplay = if (task.safeAssigneeName.isNotBlank()) task.safeAssigneeName else "Chưa gán"
        views.setTextViewText(R.id.widget_item_sub, "$timeDisplay • $projDisplay • $assigneeDisplay")

        // 4. Nhãn Tag bên phải
        when {
            !isMine -> {
                views.setTextViewText(R.id.widget_item_tag, "Chỉ xem")
                views.setInt(R.id.widget_item_tag, "setBackgroundResource", R.drawable.widget_tag_readonly)
                views.setTextColor(R.id.widget_item_tag, Color.parseColor("#64748B"))
            }
            task.isCompleted -> {
                views.setTextViewText(R.id.widget_item_tag, "Xong")
                views.setInt(R.id.widget_item_tag, "setBackgroundResource", R.drawable.widget_tag_done)
                views.setTextColor(R.id.widget_item_tag, Color.parseColor("#16A34A"))
            }
            isOverdue -> {
                views.setTextViewText(R.id.widget_item_tag, "Trễ hạn")
                views.setInt(R.id.widget_item_tag, "setBackgroundResource", R.drawable.widget_tag_overdue)
                views.setTextColor(R.id.widget_item_tag, Color.parseColor("#DC2626"))
            }
            isUrgent -> {
                views.setTextViewText(R.id.widget_item_tag, "Khẩn")
                views.setInt(R.id.widget_item_tag, "setBackgroundResource", R.drawable.widget_tag_urgent)
                views.setTextColor(R.id.widget_item_tag, Color.parseColor("#D97706"))
            }
            else -> {
                val dept = if (task.safePhongBan.isNotBlank()) task.safePhongBan else "Cá nhân"
                views.setTextViewText(R.id.widget_item_tag, dept)
                views.setInt(R.id.widget_item_tag, "setBackgroundResource", R.drawable.widget_tag_readonly)
                views.setTextColor(R.id.widget_item_tag, Color.parseColor("#5B4DF6"))
            }
        }

        // 5. Nhấn vào dòng mở chi tiết trong app
        val rowIntent = Intent().apply {
            putExtra(SeaZenWidgetProvider.EXTRA_TASK_ID, task.id)
            putExtra(SeaZenWidgetProvider.EXTRA_ACTION_TYPE, SeaZenWidgetProvider.ACTION_TYPE_OPEN_DETAIL)
            putExtra(SeaZenWidgetProvider.EXTRA_IS_MINE, isMine)
        }
        views.setOnClickFillInIntent(R.id.widget_item_row, rowIntent)

        return views
    }

    private fun isMyTask(task: TaskItem): Boolean {
        if (currentUserEmail.isBlank()) return true
        val email = task.safeAssigneeEmail.lowercase().trim()
        return email.contains(currentUserEmail) || task.safeAssigneeName.lowercase().contains("sang")
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = if (position in taskList.indices) taskList[position].id else position.toLong()
    override fun hasStableIds(): Boolean = true

    private fun formatShortTime(raw: String?): String {
        if (raw.isNullOrBlank()) return "Cả ngày"
        return try {
            if (raw.contains("T")) {
                val timePart = raw.split("T")[1].substring(0, 5)
                timePart
            } else {
                val parts = raw.trim().split("-")
                if (parts.size == 3) "${parts[2]}/${parts[1]}" else raw
            }
        } catch (_: Exception) {
            "Cả ngày"
        }
    }
}
