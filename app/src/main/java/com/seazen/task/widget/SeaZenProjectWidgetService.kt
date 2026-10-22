package com.seazen.task.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.seazen.task.R
import com.seazen.task.data.TaskChinhItem
import com.seazen.task.data.TaskItem
import com.seazen.task.data.TaskRepository

class SeaZenProjectWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return SeaZenProjectWidgetViewsFactory(this.applicationContext)
    }
}

class SeaZenProjectWidgetViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var projectList: List<Pair<TaskChinhItem, Int>> = emptyList()

    override fun onCreate() {
        loadData()
    }

    override fun onDataSetChanged() {
        loadData()
    }

    private fun loadData() {
        try {
            val repository = TaskRepository(context)
            val allProjects = repository.taskChinhList.value
            val allTasks = repository.tasks.value
            val userEmail = repository.getSavedEmail().lowercase().trim()

            // Lọc các dự án mà người dùng phụ trách hoặc dự án của phòng ban
            val filtered = if (userEmail.isNotBlank()) {
                val myProj = allProjects.filter {
                    it.safeResponsibleEmail.lowercase().contains(userEmail) ||
                            it.safeResponsiblePerson.lowercase().contains("sang")
                }
                if (myProj.isNotEmpty()) myProj else allProjects
            } else allProjects

            // Tính toán tiến độ thực tế từ các công việc con
            projectList = filtered.map { proj ->
                val childTasks = allTasks.filter { it.taskChinhId == proj.id }
                val progress = proj.computedProgress(childTasks)
                Pair(proj, progress)
            }.sortedWith(
                compareBy<Pair<TaskChinhItem, Int>> { it.second >= 100 } // Dự án chưa xong đưa lên trước
                    .thenByDescending { it.first.id }
            )
        } catch (_: Exception) {
            projectList = emptyList()
        }
    }

    override fun onDestroy() {
        projectList = emptyList()
    }

    override fun getCount(): Int = projectList.size

    override fun getViewAt(position: Int): RemoteViews? {
        if (position !in projectList.indices) return null
        val (proj, progress) = projectList[position]
        val views = RemoteViews(context.packageName, R.layout.widget_project_item)

        // 1. Phòng ban & Tiêu đề
        views.setTextViewText(R.id.widget_project_dept, proj.safePhongBan)
        views.setTextViewText(R.id.widget_project_title, proj.safeTitle)

        // 2. Tiến độ & Progress bar
        views.setTextViewText(R.id.widget_project_percent, "$progress%")
        views.setProgressBar(R.id.widget_project_progress, 100, progress, false)

        // 3. Người phụ trách & Trạng thái
        val personText = if (proj.safeResponsiblePerson.isNotBlank()) {
            "Phụ trách: ${proj.safeResponsiblePerson}"
        } else {
            "Phòng ban: ${proj.safePhongBan}"
        }
        views.setTextViewText(R.id.widget_project_person, personText)

        val statusText = when {
            progress >= 100 -> "Hoàn thành"
            progress > 0 -> "Đang làm"
            else -> proj.safeTrangThai
        }
        views.setTextViewText(R.id.widget_project_status, statusText)

        // 4. Fill-in intent để mở chi tiết dự án trong app
        val fillInIntent = Intent().apply {
            putExtra(SeaZenProjectWidgetProvider.EXTRA_PROJECT_ID, proj.id)
        }
        views.setOnClickFillInIntent(R.id.widget_project_row, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long {
        return if (position in projectList.indices) projectList[position].first.id else position.toLong()
    }

    override fun hasStableIds(): Boolean = true
}
