package com.seazen.task.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.seazen.task.R
import com.seazen.task.data.TaskRepository
import com.seazen.task.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SeaZenProjectWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PROJECT_CLICK -> {
                val projId = intent.getLongExtra(EXTRA_PROJECT_ID, -1L)
                if (projId > 0L) {
                    val mainIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("open_project_id", projId)
                    }
                    context.startActivity(mainIntent)
                }
            }
            ACTION_REFRESH_PROJECTS -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val repository = TaskRepository(context.applicationContext)
                    repository.fetchAllFromSharePoint()
                    notifyAllWidgets(context)
                }
            }
            ACTION_OPEN_DEPT -> {
                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("open_tab", 1) // Tab Phòng ban
                }
                context.startActivity(mainIntent)
            }
        }
    }

    companion object {
        const val ACTION_PROJECT_CLICK = "com.seazen.task.ACTION_PROJECT_CLICK"
        const val ACTION_REFRESH_PROJECTS = "com.seazen.task.ACTION_REFRESH_PROJECTS"
        const val ACTION_OPEN_DEPT = "com.seazen.task.ACTION_OPEN_DEPT"
        const val EXTRA_PROJECT_ID = "extra_project_id"

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            try {
                val repository = TaskRepository(context)
                val projectList = repository.taskChinhList.value
                val userEmail = repository.getSavedEmail().lowercase().trim()

                val myProjects = if (userEmail.isNotBlank()) {
                    val filtered = projectList.filter {
                        it.safeResponsibleEmail.lowercase().contains(userEmail) ||
                                it.safeResponsiblePerson.lowercase().contains("sang")
                    }
                    if (filtered.isNotEmpty()) filtered else projectList
                } else projectList

                val views = RemoteViews(context.packageName, R.layout.widget_seazen_project)

                views.setTextViewText(R.id.widget_project_header_count, "${myProjects.size} dự án đang theo dõi")

                // Adapter cho danh sách dự án
                val serviceIntent = Intent(context, SeaZenProjectWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                }
                views.setRemoteAdapter(R.id.widget_project_list, serviceIntent)
                views.setEmptyView(R.id.widget_project_list, R.id.widget_project_empty_view)

                // PendingIntentTemplate cho ListView
                val itemClickIntent = Intent(context, SeaZenProjectWidgetProvider::class.java).apply {
                    action = ACTION_PROJECT_CLICK
                }
                val itemClickPendingIntent = PendingIntent.getBroadcast(
                    context, 3001, itemClickIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                views.setPendingIntentTemplate(R.id.widget_project_list, itemClickPendingIntent)

                // Nút Refresh
                val refreshIntent = Intent(context, SeaZenProjectWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH_PROJECTS
                }
                val refreshPendingIntent = PendingIntent.getBroadcast(
                    context, 3002, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_project_btn_refresh, refreshPendingIntent)

                // Nút mở Phòng ban
                val openDeptIntent = Intent(context, SeaZenProjectWidgetProvider::class.java).apply {
                    action = ACTION_OPEN_DEPT
                }
                val openDeptPendingIntent = PendingIntent.getBroadcast(
                    context, 3003, openDeptIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_project_btn_open_dept, openDeptPendingIntent)

                appWidgetManager.updateAppWidget(appWidgetId, views)
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_project_list)
            } catch (_: Exception) {}
        }

        private fun notifyAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, SeaZenProjectWidgetProvider::class.java))
                for (id in ids) {
                    updateAppWidget(context, appWidgetManager, id)
                }
                appWidgetManager.notifyAppWidgetViewDataChanged(ids, R.id.widget_project_list)
            } catch (_: Exception) {}
        }
    }
}
