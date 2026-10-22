package com.seazen.task.data

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.seazen.task.R
import com.seazen.task.widget.SeaZenOverviewWidgetProvider
import com.seazen.task.widget.SeaZenProjectWidgetProvider
import com.seazen.task.widget.SeaZenWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class TaskRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("seazen_task_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Danh sach Task Chi Tiet (Viec con)
    private val _tasks = MutableStateFlow<List<TaskItem>>(emptyList())
    val tasks: StateFlow<List<TaskItem>> = _tasks.asStateFlow()

    // Danh sach Task Chinh (Du an / Muc tieu lon)
    private val _taskChinhList = MutableStateFlow<List<TaskChinhItem>>(emptyList())
    val taskChinhList: StateFlow<List<TaskChinhItem>> = _taskChinhList.asStateFlow()

    // Danh sach Nhan su phan quyen (DanhSachPhanQuyen)
    private val _users = MutableStateFlow<List<SeaZenUser>>(emptyList())
    val users: StateFlow<List<SeaZenUser>> = _users.asStateFlow()

    init {
        loadCachedData()
    }

    fun loadCachedData() {
        try {
            val rawTasks = prefs.getString("tasks_json", null)
            if (!rawTasks.isNullOrBlank()) {
                val type = object : TypeToken<List<TaskItem>>() {}.type
                val parsed: List<TaskItem>? = gson.fromJson(rawTasks, type)
                _tasks.value = parsed?.filterNotNull() ?: emptyList()
            } else {
                _tasks.value = emptyList()
            }
        } catch (_: Exception) {
            _tasks.value = emptyList()
        }

        try {
            val rawChinh = prefs.getString("task_chinh_json", null)
            if (!rawChinh.isNullOrBlank()) {
                val type = object : TypeToken<List<TaskChinhItem>>() {}.type
                val parsed: List<TaskChinhItem>? = gson.fromJson(rawChinh, type)
                _taskChinhList.value = parsed?.filterNotNull() ?: emptyList()
            } else {
                _taskChinhList.value = emptyList()
            }
        } catch (_: Exception) {
            _taskChinhList.value = emptyList()
        }

        try {
            val rawUsers = prefs.getString("users_json", null)
            if (!rawUsers.isNullOrBlank()) {
                val type = object : TypeToken<List<SeaZenUser>>() {}.type
                val parsed: List<SeaZenUser>? = gson.fromJson(rawUsers, type)
                _users.value = parsed?.filterNotNull() ?: getFallbackUsers()
            } else {
                _users.value = getFallbackUsers()
            }
        } catch (_: Exception) {
            _users.value = getFallbackUsers()
        }
    }

    fun saveTasks(newTasks: List<TaskItem>) {
        _tasks.value = newTasks
        prefs.edit().putString("tasks_json", gson.toJson(newTasks)).apply()
        notifyWidgetUpdate()
    }

    fun saveTaskChinh(newList: List<TaskChinhItem>) {
        _taskChinhList.value = newList
        prefs.edit().putString("task_chinh_json", gson.toJson(newList)).apply()
        notifyWidgetUpdate()
    }

    fun saveUsers(newUsers: List<SeaZenUser>) {
        _users.value = newUsers
        prefs.edit().putString("users_json", gson.toJson(newUsers)).apply()
    }

    fun notifyWidgetUpdate() {
        try {
            val widgetManager = AppWidgetManager.getInstance(context)

            // 1. Cập nhật SeaZenWidgetProvider (Việc Hôm Nay) - Thực thi trực tiếp tức thời
            SeaZenWidgetProvider.notifyAllWidgets(context)
            val todayIds = widgetManager.getAppWidgetIds(ComponentName(context, SeaZenWidgetProvider::class.java))
            if (todayIds.isNotEmpty()) {
                val intent = Intent(context, SeaZenWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, todayIds)
                }
                context.sendBroadcast(intent)
                widgetManager.notifyAppWidgetViewDataChanged(todayIds, R.id.widget_task_list)
            }

            // 2. Cập nhật SeaZenOverviewWidgetProvider (Thống kê tổng quan)
            val overviewIds = widgetManager.getAppWidgetIds(ComponentName(context, SeaZenOverviewWidgetProvider::class.java))
            if (overviewIds.isNotEmpty()) {
                val intent = Intent(context, SeaZenOverviewWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, overviewIds)
                }
                context.sendBroadcast(intent)
            }

            // 3. Cập nhật SeaZenProjectWidgetProvider (Tiến độ dự án)
            val projectIds = widgetManager.getAppWidgetIds(ComponentName(context, SeaZenProjectWidgetProvider::class.java))
            if (projectIds.isNotEmpty()) {
                val intent = Intent(context, SeaZenProjectWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, projectIds)
                }
                context.sendBroadcast(intent)
                widgetManager.notifyAppWidgetViewDataChanged(projectIds, R.id.widget_project_list)
            }
        } catch (_: Exception) {}
    }

    // Auth & Endpoints
    val authManager = MicrosoftAuthManager()

    suspend fun loginMicrosoft(email: String, password: String): MicrosoftAuthManager.AuthResult {
        return authManager.loginWithUsernamePassword(email, password)
    }

    fun saveRefreshToken(refreshToken: String) {
        prefs.edit().putString("ms_refresh_token", refreshToken.trim()).apply()
    }

    fun getSavedRefreshToken(): String {
        return prefs.getString("ms_refresh_token", "") ?: ""
    }

    fun getSavedToken(): String = prefs.getString("ms_token", "") ?: ""
    fun saveToken(token: String) = prefs.edit().putString("ms_token", token.trim()).apply()

    fun isTokenExpired(token: String): Boolean {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return true
            val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP))
            val json = gson.fromJson(payload, JsonObject::class.java)
            val exp = json.get("exp")?.asLong ?: 0L
            val nowSec = System.currentTimeMillis() / 1000
            nowSec >= (exp - 180)
        } catch (_: Exception) {
            true
        }
    }

    suspend fun getValidToken(): String? {
        val currentToken = getSavedToken()
        if (currentToken.isNotBlank() && !isTokenExpired(currentToken)) {
            return currentToken
        }
        val rt = getSavedRefreshToken()
        if (rt.isNotBlank()) {
            val refreshRes = authManager.refreshAccessToken(rt)
            if (refreshRes.success && refreshRes.accessToken != null) {
                saveToken(refreshRes.accessToken)
                if (refreshRes.refreshToken != null) {
                    saveRefreshToken(refreshRes.refreshToken)
                }
                return refreshRes.accessToken
            }
        }
        return currentToken.ifBlank { null }
    }

    suspend fun forceRefreshToken(): String? {
        val rt = getSavedRefreshToken()
        if (rt.isBlank()) return null
        val refreshRes = authManager.refreshAccessToken(rt)
        return if (refreshRes.success && refreshRes.accessToken != null) {
            saveToken(refreshRes.accessToken)
            if (refreshRes.refreshToken != null) {
                saveRefreshToken(refreshRes.refreshToken)
            }
            refreshRes.accessToken
        } else {
            null
        }
    }

    fun getSavedEmail(): String = prefs.getString("user_email", "sang.nguyen@seazen.com.vn") ?: ""
    fun saveEmail(email: String) = prefs.edit().putString("user_email", email.trim()).apply()

    fun getSiteUrl(): String = prefs.getString("sp_site_url", "https://seazenvn.sharepoint.com/sites/ONESEAZEN") ?: "https://seazenvn.sharepoint.com/sites/ONESEAZEN"
    fun getListChiTietId(): String = prefs.getString("sp_list_chitiet_id", "fd83aa4a-a642-43c2-b2f0-d34079e850de") ?: "fd83aa4a-a642-43c2-b2f0-d34079e850de"
    fun getListChinhId(): String = prefs.getString("sp_list_chinh_id", "4d195cd8-53a0-472e-bdec-5a75c7062c14") ?: "4d195cd8-53a0-472e-bdec-5a75c7062c14"
    fun getListPhanQuyenId(): String = prefs.getString("sp_list_phanquyen_id", "f7f3a310-a824-4ba9-a673-bf0038a423b9") ?: "f7f3a310-a824-4ba9-a673-bf0038a423b9"

    fun logout() {
        prefs.edit().remove("ms_token").remove("ms_refresh_token").apply()
    }

    fun isConfigured(): Boolean = getSavedToken().isNotBlank()

    // Che do doc/ghi: Luon luon cho phep doc ghi truc tiep len SharePoint
    fun isReadOnlyMode(): Boolean = false
    fun setReadOnlyMode(enabled: Boolean) { /* Luon cho phep doc ghi truc tiep */ }

    // Cai dat Giao dien (Dark/Light) va Ngon ngu (vi/en)
    fun isDarkMode(): Boolean = prefs.getBoolean("is_dark_mode", false)
    fun setDarkMode(enabled: Boolean) = prefs.edit().putBoolean("is_dark_mode", enabled).apply()

    fun getAppLanguage(): String = prefs.getString("app_language", "vi") ?: "vi"
    fun setAppLanguage(lang: String) = prefs.edit().putString("app_language", lang).apply()

    // Loc cong viec theo ngay
    fun getTasksForDate(date: LocalDate): List<TaskItem> {
        return _tasks.value.filter { it.isActiveOn(date) }
    }

    fun getTaskCountForDate(date: LocalDate): Int {
        return _tasks.value.count { it.isActiveOn(date) }
    }

    // ==========================================
    // HANG DOI OFFLINE / MANG LAG (PENDING QUEUE)
    // ==========================================
    fun getPendingCreateChinh(): List<TaskChinhItem> {
        val raw = prefs.getString("pending_create_chinh", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<TaskChinhItem>>() {}.type
            gson.fromJson<List<TaskChinhItem>>(raw, type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private fun savePendingCreateChinh(list: List<TaskChinhItem>) {
        prefs.edit().putString("pending_create_chinh", gson.toJson(list)).apply()
    }

    private fun addPendingCreateChinh(item: TaskChinhItem) {
        val current = getPendingCreateChinh().filter { it.id != item.id }
        savePendingCreateChinh(current + item)
    }

    private fun removePendingCreateChinh(id: Long) {
        val current = getPendingCreateChinh().filter { it.id != id }
        savePendingCreateChinh(current)
    }

    fun getPendingCreateChiTiet(): List<TaskItem> {
        val raw = prefs.getString("pending_create_chitiet", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<TaskItem>>() {}.type
            gson.fromJson<List<TaskItem>>(raw, type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private fun savePendingCreateChiTiet(list: List<TaskItem>) {
        prefs.edit().putString("pending_create_chitiet", gson.toJson(list)).apply()
    }

    private fun addPendingCreateChiTiet(item: TaskItem) {
        val current = getPendingCreateChiTiet().filter { it.id != item.id }
        savePendingCreateChiTiet(current + item)
    }

    private fun removePendingCreateChiTiet(id: Long) {
        val current = getPendingCreateChiTiet().filter { it.id != id }
        savePendingCreateChiTiet(current)
    }

    private fun getPendingToggleTasks(): Map<Long, Boolean> {
        val raw = prefs.getString("pending_toggle_tasks", null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<Long, Boolean>>() {}.type
            gson.fromJson<Map<Long, Boolean>>(raw, type) ?: emptyMap()
        } catch (_: Exception) { emptyMap() }
    }

    private fun savePendingToggleTasks(map: Map<Long, Boolean>) {
        prefs.edit().putString("pending_toggle_tasks", gson.toJson(map)).apply()
    }

    private fun addPendingToggle(taskId: Long, isDone: Boolean) {
        val m = getPendingToggleTasks().toMutableMap()
        m[taskId] = isDone
        savePendingToggleTasks(m)
    }

    private fun removePendingToggle(taskId: Long) {
        val m = getPendingToggleTasks().toMutableMap()
        m.remove(taskId)
        savePendingToggleTasks(m)
    }

    // Tu dong day hang doi cho dong bo len SharePoint khi co ket noi
    private suspend fun flushPendingSync(token: String) {
        // 1. Flush TaskChinh pending
        val pendingChinh = getPendingCreateChinh()
        for (item in pendingChinh) {
            try {
                val res = executeCreateTaskChinh(item.safeTitle, item.safeStartDate, item.safeEndDate, item.safeResponsiblePerson, item.safeResponsibleEmail, item.safePhongBan, item.safePhanLoaiTask, item.safePhanLoaiViec, item.safeCreatorName, item.safeCreatorEmail, item.safeCcName, item.safeCcEmail, token)
                if (res != null) {
                    removePendingCreateChinh(item.id)
                    val currentChinh = _taskChinhList.value.map { if (it.id == item.id) res else it }
                    saveTaskChinh(currentChinh)
                    val currentTasks = _tasks.value.map { if (it.taskChinhId == item.id) it.copy(taskChinhId = res.id) else it }
                    saveTasks(currentTasks)
                }
            } catch (_: Exception) {}
        }

        // 2. Flush TaskChiTiet pending
        val pendingChiTiet = getPendingCreateChiTiet()
        for (item in pendingChiTiet) {
            try {
                val res = executeCreateTaskChiTiet(item.safeTitle, item.taskChinhId, item.safeTaskChinhTitle, item.safeStartDate, item.safeEndDate, item.safeAssigneeName, item.safeAssigneeEmail, token)
                if (res != null) {
                    removePendingCreateChiTiet(item.id)
                    val currentTasks = _tasks.value.map { if (it.id == item.id) res else it }
                    saveTasks(currentTasks)
                }
            } catch (_: Exception) {}
        }

        // 3. Flush Pending Toggles
        val pendingToggles = getPendingToggleTasks()
        for ((tId, isDone) in pendingToggles) {
            try {
                val ok = executeToggleTaskCompletion(tId, isDone, token)
                if (ok) {
                    removePendingToggle(tId)
                }
            } catch (_: Exception) {}
        }
    }

    // ==========================================
    // DONG BO DA LUONG TANG TOC TU SHAREPOINT (SSOT)
    // ==========================================
    suspend fun fetchAllFromSharePoint(token: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        var activeToken = token?.trim()?.ifBlank { null } ?: getValidToken() ?: ""
        if (activeToken.isBlank()) {
            return@withContext Result.failure(Exception("Chua co phien lam viec."))
        }

        val siteUrl = getSiteUrl().removeSuffix("/")
        val chitietId = getListChiTietId()
        val chinhId = getListChinhId()
        val phanquyenId = getListPhanQuyenId()

        try {
            // Day hang doi offline neu co
            flushPendingSync(activeToken)

            // Goi song song ca 3 endpoint de tang toc do reload gap doi
            coroutineScope {
                val defChinh = async { fetchRawChinh(siteUrl, chinhId, activeToken) }
                val defChiTiet = async { fetchRawChiTiet(siteUrl, chitietId, activeToken) }
                val defUsers = async { fetchRawUsers(siteUrl, phanquyenId, activeToken) }

                val (serverChinh, newTok1) = defChinh.await()
                if (newTok1 != null) activeToken = newTok1

                val (serverChiTiet, newTok2) = defChiTiet.await()
                if (newTok2 != null) activeToken = newTok2

                val serverUsers = defUsers.await()

                // SINGLE SOURCE OF TRUTH (SSOT):
                // Chi giu nhung gi thuc su co tren SharePoint + items offline cho ghi
                val pendingChinh = getPendingCreateChinh()
                val pendingChiTiet = getPendingCreateChiTiet()

                val finalChinh = (pendingChinh + serverChinh).distinctBy { it.id }
                val finalTasks = (pendingChiTiet + serverChiTiet).distinctBy { it.id }

                // Tinh toan lai tien do tu dau viec thuc te
                val synchronizedChinh = finalChinh.map { p ->
                    val relatedSubs = finalTasks.filter { it.taskChinhId == p.id }
                    if (relatedSubs.isNotEmpty()) {
                        val compProg = p.computedProgress(relatedSubs)
                        val compStatus = when {
                            relatedSubs.all { it.isCompleted } -> "Hoan thanh"
                            compProg > 0 -> "Dang lam"
                            else -> p.safeTrangThai
                        }
                        p.copy(progress = compProg, trangThai = compStatus)
                    } else p
                }

                saveTaskChinh(synchronizedChinh)
                saveTasks(finalTasks)
                if (serverUsers.isNotEmpty()) {
                    saveUsers(serverUsers)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchRawChinh(siteUrl: String, chinhId: String, token: String): Pair<List<TaskChinhItem>, String?> {
        var activeToken = token
        var newTok: String? = null
        val urlChinh = "$siteUrl/_api/web/lists(guid'$chinhId')/items?%24top=100&%24select=ID,Title,TenNguoiChiuTrachNhiem,NguoiChiuTrachNhiemEmail,NgayBatDau,NgayKetThuc,TienDo,PhongBan,PhanLoaiTask,PhanLoaiViec,TrangThai,TenNguoiGiaoViec,NguoiGiaoViecEmail,TenNguoiNhanThongBao,NguoiNhanThongBaoEmail,NgayHanMoi,LyDoDoiHan,TrangThaiDuyetHan&%24orderby=ID%20desc"
        var req = Request.Builder()
            .url(urlChinh)
            .header("Authorization", "Bearer $activeToken")
            .header("Accept", "application/json;odata=verbose")
            .build()
        var resp = httpClient.newCall(req).execute()

        if (resp.code == 401) {
            resp.close()
            val refreshed = forceRefreshToken()
            if (refreshed != null) {
                activeToken = refreshed
                newTok = refreshed
                req = Request.Builder()
                    .url(urlChinh)
                    .header("Authorization", "Bearer $activeToken")
                    .header("Accept", "application/json;odata=verbose")
                    .build()
                resp = httpClient.newCall(req).execute()
            }
        }

        val list = mutableListOf<TaskChinhItem>()
        resp.use { r ->
            if (r.isSuccessful) {
                val bodyStr = r.body?.string() ?: "{}"
                val jsonRoot = gson.fromJson(bodyStr, JsonObject::class.java)
                val results = jsonRoot.getAsJsonObject("d")?.getAsJsonArray("results") ?: JsonArray()
                for (el in results) {
                    if (el !is JsonObject) continue
                    val pb = el.get("PhongBan")?.takeIf { !it.isJsonNull }?.asString ?: "SeaZen Tech"
                    val pl = el.get("PhanLoaiTask")?.takeIf { !it.isJsonNull }?.asString ?: "Phòng ban"
                    val plv = el.get("PhanLoaiViec")?.takeIf { !it.isJsonNull }?.asString ?: "Công việc tháng"
                    val tt = el.get("TrangThai")?.takeIf { !it.isJsonNull }?.asString ?: "Đang làm"
                    val rawProg = el.get("TienDo")?.takeIf { !it.isJsonNull }?.asDouble
                    val progVal = when {
                        rawProg == null -> 0
                        rawProg <= 1.0 -> (rawProg * 100).toInt().coerceIn(0, 100)
                        else -> rawProg.toInt().coerceIn(0, 100)
                    }
                    list.add(
                        TaskChinhItem(
                            id = el.get("ID")?.asLong ?: 0L,
                            title = el.get("Title")?.takeIf { !it.isJsonNull }?.asString ?: "",
                            responsiblePerson = el.get("TenNguoiChiuTrachNhiem")?.takeIf { !it.isJsonNull }?.asString ?: "",
                            responsibleEmail = el.get("NguoiChiuTrachNhiemEmail")?.takeIf { !it.isJsonNull }?.asString ?: "",
                            startDate = el.get("NgayBatDau")?.takeIf { !it.isJsonNull }?.asString ?: "",
                            endDate = el.get("NgayKetThuc")?.takeIf { !it.isJsonNull }?.asString ?: "",
                            progress = progVal,
                            phongBan = pb,
                            phanLoaiTask = pl,
                            phanLoaiViec = plv,
                            trangThai = tt,
                            creatorName = el.get("TenNguoiGiaoViec")?.takeIf { !it.isJsonNull }?.asString ?: "",
                            creatorEmail = el.get("NguoiGiaoViecEmail")?.takeIf { !it.isJsonNull }?.asString ?: "",
                            ccName = el.get("TenNguoiNhanThongBao")?.takeIf { !it.isJsonNull }?.asString ?: "",
                            ccEmail = el.get("NguoiNhanThongBaoEmail")?.takeIf { !it.isJsonNull }?.asString ?: "",
                            newEndDate = el.get("NgayHanMoi")?.takeIf { !it.isJsonNull }?.asString ?: "",
                            extReason = el.get("LyDoDoiHan")?.takeIf { !it.isJsonNull }?.asString ?: "",
                            trangThaiDuyetHan = el.get("TrangThaiDuyetHan")?.takeIf { !it.isJsonNull }?.asString ?: "",
                            ghiChu = ""
                        )
                    )
                }
            }
        }
        return Pair(list, newTok)
    }

    private suspend fun fetchRawChiTiet(siteUrl: String, chitietId: String, token: String): Pair<List<TaskItem>, String?> {
        var activeToken = token
        var newTok: String? = null
        val urlChiTiet = "$siteUrl/_api/web/lists(guid'$chitietId')/items?%24top=200&%24select=ID,Title,TaskChinhID/Id,TaskChinhID/Title,TaskChinhIDId,DaHoanThanh,TenNguoiThucHien,NguoiThucHienEmail,NgayBatDau,NgayKetThuc,Created&%24expand=TaskChinhID&%24orderby=ID%20desc"
        var req = Request.Builder()
            .url(urlChiTiet)
            .header("Authorization", "Bearer $activeToken")
            .header("Accept", "application/json;odata=verbose")
            .build()
        var resp = httpClient.newCall(req).execute()

        if (resp.code == 401) {
            resp.close()
            val refreshed = forceRefreshToken()
            if (refreshed != null) {
                activeToken = refreshed
                newTok = refreshed
                req = Request.Builder()
                    .url(urlChiTiet)
                    .header("Authorization", "Bearer $activeToken")
                    .header("Accept", "application/json;odata=verbose")
                    .build()
                resp = httpClient.newCall(req).execute()
            }
        }

        val list = mutableListOf<TaskItem>()
        resp.use { r ->
            if (r.isSuccessful) {
                val bodyStr = r.body?.string() ?: "{}"
                val jsonRoot = gson.fromJson(bodyStr, JsonObject::class.java)
                val results = jsonRoot.getAsJsonObject("d")?.getAsJsonArray("results") ?: JsonArray()
                for (el in results) {
                    if (el !is JsonObject) continue
                    var tcTitle = ""
                    var tcId: Long? = null
                    val tcObj = el.get("TaskChinhID")
                    if (tcObj is JsonObject) {
                        tcTitle = tcObj.get("Title")?.takeIf { !it.isJsonNull }?.asString ?: ""
                        tcId = tcObj.get("Id")?.takeIf { !it.isJsonNull }?.asLong
                    }
                    if (tcId == null) {
                        tcId = el.get("TaskChinhIDId")?.takeIf { !it.isJsonNull }?.asLong
                            ?: el.get("TaskChinhId")?.takeIf { !it.isJsonNull }?.asLong
                            ?: el.get("TaskChinhIDLookupId")?.takeIf { !it.isJsonNull }?.asLong
                    }
                    val prio = "normal"
                    list.add(
                        TaskItem(
                            id = el.get("ID")?.asLong ?: 0L,
                            title = el.get("Title")?.takeIf { !it.isJsonNull }?.asString ?: "Không tiêu đề",
                            taskChinhId = tcId,
                            taskChinhTitle = tcTitle,
                            phongBan = "",
                            isCompleted = el.get("DaHoanThanh")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
                            assigneeName = el.get("TenNguoiThucHien")?.takeIf { !it.isJsonNull }?.asString ?: "",
                            assigneeEmail = el.get("NguoiThucHienEmail")?.takeIf { !it.isJsonNull }?.asString ?: "",
                            startDate = el.get("NgayBatDau")?.takeIf { !it.isJsonNull }?.asString ?: "",
                            endDate = el.get("NgayKetThuc")?.takeIf { !it.isJsonNull }?.asString ?: "",
                            priority = prio,
                            ghiChu = "",
                            created = el.get("Created")?.takeIf { !it.isJsonNull }?.asString ?: ""
                        )
                    )
                }
            }
        }
        return Pair(list, newTok)
    }

    private suspend fun fetchRawUsers(siteUrl: String, phanquyenId: String, token: String): List<SeaZenUser> {
        return try {
            val urlPhanQuyen = "$siteUrl/_api/web/lists(guid'$phanquyenId')/items?%24top=100&%24select=ID,Title,Email,ChucVu,PhongBan&%24orderby=Title%20asc"
            val req = Request.Builder()
                .url(urlPhanQuyen)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json;odata=verbose")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string() ?: "{}"
                    val jsonRoot = gson.fromJson(bodyStr, JsonObject::class.java)
                    val results = jsonRoot.getAsJsonObject("d")?.getAsJsonArray("results") ?: JsonArray()
                    val list = mutableListOf<SeaZenUser>()
                    for (el in results) {
                        if (el !is JsonObject) continue
                        val uId = el.get("ID")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
                        val uName = el.get("Title")?.takeIf { !it.isJsonNull }?.asString ?: ""
                        val uEmail = el.get("Email")?.takeIf { !it.isJsonNull }?.asString ?: ""
                        val uChucVu = el.get("ChucVu")?.takeIf { !it.isJsonNull }?.asString ?: ""
                        val uPhongBan = el.get("PhongBan")?.takeIf { !it.isJsonNull }?.asString ?: ""
                        if (uName.isNotBlank() || uEmail.isNotBlank()) {
                            list.add(SeaZenUser(uId, uName, uEmail, uChucVu, uPhongBan))
                        }
                    }
                    list
                } else emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun getFormDigest(siteUrl: String, token: String): String? {
        return try {
            val url = "$siteUrl/_api/contextinfo"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json;odata=verbose")
                .post("".toRequestBody(null))
                .build()
            httpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    json.getAsJsonObject("d")
                        ?.getAsJsonObject("GetContextWebInformation")
                        ?.get("FormDigestValue")
                        ?.asString
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    // ==========================================
    // C VA U (CREATE VA UPDATE) DONG BO SHAREPOINT
    // ==========================================

    // Them TaskChinh moi
    suspend fun createTaskChinh(
        title: String,
        startDate: String,
        endDate: String,
        responsiblePerson: String,
        responsibleEmail: String,
        phongBan: String = "SeaZen Tech",
        phanLoaiTask: String = "Phòng ban",
        phanLoaiViec: String = "Công việc tháng",
        creatorName: String = "",
        creatorEmail: String = "",
        ccName: String = "",
        ccEmail: String = ""
    ): Result<TaskChinhItem> = withContext(Dispatchers.IO) {
        val tempId = System.currentTimeMillis()
        val localItem = TaskChinhItem(
            id = tempId,
            title = title.trim(),
            responsiblePerson = responsiblePerson.trim(),
            responsibleEmail = responsibleEmail.trim(),
            startDate = startDate.trim(),
            endDate = endDate.trim(),
            progress = 0,
            phongBan = phongBan,
            phanLoaiTask = phanLoaiTask,
            phanLoaiViec = phanLoaiViec,
            trangThai = "Chưa bắt đầu",
            creatorName = creatorName.trim(),
            creatorEmail = creatorEmail.trim(),
            ccName = ccName.trim(),
            ccEmail = ccEmail.trim()
        )

        val activeToken = getValidToken()
        if (activeToken.isNullOrBlank()) {
            addPendingCreateChinh(localItem)
            saveTaskChinh(listOf(localItem) + _taskChinhList.value)
            return@withContext Result.success(localItem)
        }

        try {
            val realItem = executeCreateTaskChinh(title, startDate, endDate, responsiblePerson, responsibleEmail, phongBan, phanLoaiTask, phanLoaiViec, creatorName, creatorEmail, ccName, ccEmail, activeToken)
            if (realItem != null) {
                val currentList = _taskChinhList.value.filter { it.id != realItem.id && it.id != tempId }
                saveTaskChinh(listOf(realItem) + currentList)
                Result.success(realItem)
            } else {
                addPendingCreateChinh(localItem)
                saveTaskChinh(listOf(localItem) + _taskChinhList.value)
                Result.success(localItem)
            }
        } catch (_: Exception) {
            addPendingCreateChinh(localItem)
            saveTaskChinh(listOf(localItem) + _taskChinhList.value)
            Result.success(localItem)
        }
    }

    private suspend fun executeCreateTaskChinh(
        title: String,
        startDate: String,
        endDate: String,
        responsiblePerson: String,
        responsibleEmail: String,
        phongBan: String,
        phanLoaiTask: String,
        phanLoaiViec: String,
        creatorName: String,
        creatorEmail: String,
        ccName: String,
        ccEmail: String,
        token: String
    ): TaskChinhItem? {
        val siteUrl = getSiteUrl().removeSuffix("/")
        val chinhId = getListChinhId()
        val url = "$siteUrl/_api/web/lists(guid'$chinhId')/items"

        val jsonPayload = JsonObject().apply {
            val meta = JsonObject().apply {
                addProperty("type", "SP.Data.TaskChinhListItem")
            }
            add("__metadata", meta)
            addProperty("Title", title.trim())
            addProperty("TenNguoiChiuTrachNhiem", responsiblePerson.trim())
            addProperty("NguoiChiuTrachNhiemEmail", responsibleEmail.trim())
            addProperty("PhongBan", phongBan)
            addProperty("PhanLoaiTask", phanLoaiTask)
            addProperty("PhanLoaiViec", phanLoaiViec)
            if (creatorName.isNotBlank()) addProperty("TenNguoiGiaoViec", creatorName.trim())
            if (creatorEmail.isNotBlank()) addProperty("NguoiGiaoViecEmail", creatorEmail.trim())
            if (ccName.isNotBlank()) addProperty("TenNguoiNhanThongBao", ccName.trim())
            if (ccEmail.isNotBlank()) addProperty("NguoiNhanThongBaoEmail", ccEmail.trim())
            if (startDate.isNotBlank()) addProperty("NgayBatDau", if (startDate.length == 10) "${startDate}T00:00:00Z" else startDate)
            if (endDate.isNotBlank()) addProperty("NgayKetThuc", if (endDate.length == 10) "${endDate}T23:59:59Z" else endDate)
            addProperty("TienDo", 0)
            addProperty("TrangThai", "Chưa bắt đầu")
            addProperty("TrangThaiDuyetHan", "Không có")
        }

        val mediaType = "application/json;odata=verbose".toMediaType()
        val requestBody = gson.toJson(jsonPayload).toRequestBody(mediaType)

        var curTok = token
        var digest = getFormDigest(siteUrl, curTok)
        var reqBuilder = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $curTok")
            .header("Accept", "application/json;odata=verbose")
            .header("Content-Type", "application/json;odata=verbose")
        if (!digest.isNullOrBlank()) reqBuilder.header("X-RequestDigest", digest)
        var resp = httpClient.newCall(reqBuilder.post(requestBody).build()).execute()

        if (resp.code == 401) {
            resp.close()
            val refreshed = forceRefreshToken()
            if (refreshed != null) {
                curTok = refreshed
                digest = getFormDigest(siteUrl, curTok)
                reqBuilder = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $curTok")
                    .header("Accept", "application/json;odata=verbose")
                    .header("Content-Type", "application/json;odata=verbose")
                if (!digest.isNullOrBlank()) reqBuilder.header("X-RequestDigest", digest)
                resp = httpClient.newCall(reqBuilder.post(requestBody).build()).execute()
            }
        }

        return resp.use { r ->
            if (r.isSuccessful || r.code == 201) {
                val bodyStr = r.body?.string() ?: "{}"
                val jsonRoot = gson.fromJson(bodyStr, JsonObject::class.java)
                val realId = jsonRoot.getAsJsonObject("d")?.get("ID")?.asLong ?: return null
                TaskChinhItem(
                    id = realId,
                    title = title.trim(),
                    responsiblePerson = responsiblePerson.trim(),
                    responsibleEmail = responsibleEmail.trim(),
                    startDate = startDate.trim(),
                    endDate = endDate.trim(),
                    progress = 0,
                    phongBan = phongBan,
                    phanLoaiTask = phanLoaiTask,
                    phanLoaiViec = phanLoaiViec,
                    trangThai = "Chưa bắt đầu",
                    creatorName = creatorName.trim(),
                    creatorEmail = creatorEmail.trim(),
                    ccName = ccName.trim(),
                    ccEmail = ccEmail.trim()
                )
            } else null
        }
    }

    // Them TaskChiTiet moi
    suspend fun createTaskChiTiet(
        title: String,
        taskChinhId: Long?,
        taskChinhTitle: String,
        startDate: String,
        endDate: String,
        assigneeName: String,
        assigneeEmail: String
    ): Result<TaskItem> = withContext(Dispatchers.IO) {
        val tempId = System.currentTimeMillis()
        val parent = if (taskChinhId != null) _taskChinhList.value.find { it.id == taskChinhId } else null
        val resolvedParentTitle = if (taskChinhTitle.isNotBlank()) taskChinhTitle else (parent?.safeTitle ?: "")
        val resolvedPhongBan = parent?.safePhongBan ?: ""
        val localItem = TaskItem(
            id = tempId,
            title = title.trim(),
            taskChinhId = taskChinhId,
            taskChinhTitle = resolvedParentTitle.trim(),
            isCompleted = false,
            assigneeName = assigneeName.trim(),
            assigneeEmail = assigneeEmail.trim(),
            startDate = startDate.trim(),
            endDate = endDate.trim(),
            phongBan = resolvedPhongBan
        )

        val activeToken = getValidToken()
        if (activeToken.isNullOrBlank()) {
            addPendingCreateChiTiet(localItem)
            val updatedTasks = listOf(localItem) + _tasks.value
            saveTasks(updatedTasks)
            if (taskChinhId != null) recalculateParentProgress(taskChinhId, updatedTasks)
            return@withContext Result.success(localItem)
        }

        try {
            val realItem = executeCreateTaskChiTiet(title, taskChinhId, resolvedParentTitle, startDate, endDate, assigneeName, assigneeEmail, activeToken)
            if (realItem != null) {
                val updatedTasks = listOf(realItem) + _tasks.value.filter { it.id != realItem.id && it.id != tempId }
                saveTasks(updatedTasks)
                if (taskChinhId != null) recalculateParentProgress(taskChinhId, updatedTasks)
                Result.success(realItem)
            } else {
                addPendingCreateChiTiet(localItem)
                val updatedTasks = listOf(localItem) + _tasks.value
                saveTasks(updatedTasks)
                if (taskChinhId != null) recalculateParentProgress(taskChinhId, updatedTasks)
                Result.success(localItem)
            }
        } catch (_: Exception) {
            addPendingCreateChiTiet(localItem)
            val updatedTasks = listOf(localItem) + _tasks.value
            saveTasks(updatedTasks)
            if (taskChinhId != null) recalculateParentProgress(taskChinhId, updatedTasks)
            Result.success(localItem)
        }
    }

    private suspend fun executeCreateTaskChiTiet(
        title: String,
        taskChinhId: Long?,
        taskChinhTitle: String,
        startDate: String,
        endDate: String,
        assigneeName: String,
        assigneeEmail: String,
        token: String
    ): TaskItem? {
        val siteUrl = getSiteUrl().removeSuffix("/")
        val chitietId = getListChiTietId()
        val url = "$siteUrl/_api/web/lists(guid'$chitietId')/items"

        val jsonPayload = JsonObject().apply {
            val meta = JsonObject().apply {
                addProperty("type", "SP.Data.TaskChiTietListItem")
            }
            add("__metadata", meta)
            addProperty("Title", title.trim())
            if (taskChinhId != null && taskChinhId > 0) {
                addProperty("TaskChinhIDId", taskChinhId)
            }
            addProperty("TenNguoiThucHien", assigneeName.trim())
            addProperty("NguoiThucHienEmail", assigneeEmail.trim())
            if (startDate.isNotBlank()) addProperty("NgayBatDau", if (startDate.length == 10) "${startDate}T00:00:00Z" else startDate)
            if (endDate.isNotBlank()) addProperty("NgayKetThuc", if (endDate.length == 10) "${endDate}T23:59:59Z" else endDate)
            addProperty("DaHoanThanh", false)
        }

        val mediaType = "application/json;odata=verbose".toMediaType()
        val requestBody = gson.toJson(jsonPayload).toRequestBody(mediaType)

        var curTok = token
        var digest = getFormDigest(siteUrl, curTok)
        var reqBuilder = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $curTok")
            .header("Accept", "application/json;odata=verbose")
            .header("Content-Type", "application/json;odata=verbose")
        if (!digest.isNullOrBlank()) reqBuilder.header("X-RequestDigest", digest)
        var resp = httpClient.newCall(reqBuilder.post(requestBody).build()).execute()

        if (resp.code == 401) {
            resp.close()
            val refreshed = forceRefreshToken()
            if (refreshed != null) {
                curTok = refreshed
                digest = getFormDigest(siteUrl, curTok)
                reqBuilder = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $curTok")
                    .header("Accept", "application/json;odata=verbose")
                    .header("Content-Type", "application/json;odata=verbose")
                if (!digest.isNullOrBlank()) reqBuilder.header("X-RequestDigest", digest)
                resp = httpClient.newCall(reqBuilder.post(requestBody).build()).execute()
            }
        }

        return resp.use { r ->
            if (r.isSuccessful || r.code == 201) {
                val bodyStr = r.body?.string() ?: "{}"
                val jsonRoot = gson.fromJson(bodyStr, JsonObject::class.java)
                val realId = jsonRoot.getAsJsonObject("d")?.get("ID")?.asLong ?: return null
                TaskItem(
                    id = realId,
                    title = title.trim(),
                    taskChinhId = taskChinhId,
                    taskChinhTitle = taskChinhTitle.trim(),
                    isCompleted = false,
                    assigneeName = assigneeName.trim(),
                    assigneeEmail = assigneeEmail.trim(),
                    startDate = startDate.trim(),
                    endDate = endDate.trim(),
                    phongBan = ""
                )
            } else null
        }
    }

    // Toggle hoan thanh TaskChiTiet
    suspend fun toggleTaskCompletion(taskId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        val currentList = _tasks.value
        val target = currentList.firstOrNull { it.id == taskId }
            ?: return@withContext Result.failure(Exception("Không tìm thấy công việc."))

        val newDoneStatus = !target.isCompleted
        val updated = currentList.map {
            if (it.id == taskId) it.copy(isCompleted = newDoneStatus) else it
        }
        saveTasks(updated)
        recalculateParentProgress(target.taskChinhId, updated)

        val activeToken = getValidToken()
        if (activeToken.isNullOrBlank()) {
            addPendingToggle(taskId, newDoneStatus)
            return@withContext Result.success(newDoneStatus)
        }

        try {
            val ok = executeToggleTaskCompletion(taskId, newDoneStatus, activeToken)
            if (ok) {
                removePendingToggle(taskId)
                Result.success(newDoneStatus)
            } else {
                addPendingToggle(taskId, newDoneStatus)
                Result.success(newDoneStatus)
            }
        } catch (_: Exception) {
            addPendingToggle(taskId, newDoneStatus)
            Result.success(newDoneStatus)
        }
    }

    private suspend fun executeToggleTaskCompletion(taskId: Long, isDone: Boolean, token: String): Boolean {
        val siteUrl = getSiteUrl().removeSuffix("/")
        val chitietId = getListChiTietId()
        val url = "$siteUrl/_api/web/lists(guid'$chitietId')/items($taskId)"

        val jsonPayload = JsonObject().apply {
            val meta = JsonObject().apply {
                addProperty("type", "SP.Data.TaskChiTietListItem")
            }
            add("__metadata", meta)
            addProperty("DaHoanThanh", isDone)
        }

        val mediaType = "application/json;odata=verbose".toMediaType()
        val requestBody = gson.toJson(jsonPayload).toRequestBody(mediaType)

        var curTok = token
        var digest = getFormDigest(siteUrl, curTok)
        var reqBuilder = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $curTok")
            .header("Accept", "application/json;odata=verbose")
            .header("Content-Type", "application/json;odata=verbose")
            .header("X-HTTP-Method", "MERGE")
            .header("If-Match", "*")
        if (!digest.isNullOrBlank()) reqBuilder.header("X-RequestDigest", digest)
        var resp = httpClient.newCall(reqBuilder.post(requestBody).build()).execute()

        if (resp.code == 401) {
            resp.close()
            val refreshed = forceRefreshToken()
            if (refreshed != null) {
                curTok = refreshed
                digest = getFormDigest(siteUrl, curTok)
                reqBuilder = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $curTok")
                    .header("Accept", "application/json;odata=verbose")
                    .header("Content-Type", "application/json;odata=verbose")
                    .header("X-HTTP-Method", "MERGE")
                    .header("If-Match", "*")
                if (!digest.isNullOrBlank()) reqBuilder.header("X-RequestDigest", digest)
                resp = httpClient.newCall(reqBuilder.post(requestBody).build()).execute()
            }
        }

        return resp.use { it.isSuccessful || it.code == 204 }
    }

    // Dang ky doi han cong viec chinh
    suspend fun requestTaskExtension(taskId: Long, newDate: String, reason: String): Result<Unit> = withContext(Dispatchers.IO) {
        val currentList = _taskChinhList.value
        val target = currentList.find { it.id == taskId }
            ?: return@withContext Result.failure(Exception("Không tìm thấy công việc chính."))

        val updated = currentList.map {
            if (it.id == taskId) it.copy(newEndDate = newDate.trim(), extReason = reason.trim(), trangThaiDuyetHan = "Chờ duyệt") else it
        }
        saveTaskChinh(updated)

        val activeToken = getValidToken() ?: ""
        if (activeToken.isBlank()) {
            return@withContext Result.success(Unit)
        }

        try {
            val siteUrl = getSiteUrl().removeSuffix("/")
            val chinhId = getListChinhId()
            val url = "$siteUrl/_api/web/lists(guid'$chinhId')/items($taskId)"

            val jsonPayload = JsonObject().apply {
                val meta = JsonObject().apply {
                    addProperty("type", "SP.Data.TaskChinhListItem")
                }
                add("__metadata", meta)
                addProperty("NgayHanMoi", if (newDate.length == 10) "${newDate}T23:59:59Z" else newDate)
                addProperty("LyDoDoiHan", reason.trim())
                addProperty("TrangThaiDuyetHan", "Chờ duyệt")
            }

            val mediaType = "application/json;odata=verbose".toMediaType()
            val requestBody = gson.toJson(jsonPayload).toRequestBody(mediaType)

            var curTok = activeToken
            var digest = getFormDigest(siteUrl, curTok)
            var reqBuilder = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $curTok")
                .header("Accept", "application/json;odata=verbose")
                .header("Content-Type", "application/json;odata=verbose")
                .header("X-HTTP-Method", "MERGE")
                .header("If-Match", "*")
            if (!digest.isNullOrBlank()) reqBuilder.header("X-RequestDigest", digest)
            var resp = httpClient.newCall(reqBuilder.post(requestBody).build()).execute()

            if (resp.code == 401) {
                resp.close()
                val refreshed = forceRefreshToken()
                if (refreshed != null) {
                    curTok = refreshed
                    digest = getFormDigest(siteUrl, curTok)
                    reqBuilder = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $curTok")
                        .header("Accept", "application/json;odata=verbose")
                        .header("Content-Type", "application/json;odata=verbose")
                        .header("X-HTTP-Method", "MERGE")
                        .header("If-Match", "*")
                    if (!digest.isNullOrBlank()) reqBuilder.header("X-RequestDigest", digest)
                    resp = httpClient.newCall(reqBuilder.post(requestBody).build()).execute()
                }
            }

            resp.use { r ->
                if (r.isSuccessful || r.code == 204) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("HTTP ${r.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Xoa TaskChiTiet
    suspend fun deleteTaskChiTiet(taskId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val currentList = _tasks.value
        val target = currentList.find { it.id == taskId }
        val updated = currentList.filter { it.id != taskId }
        saveTasks(updated)
        if (target?.taskChinhId != null) {
            recalculateParentProgress(target.taskChinhId, updated)
        }

        val activeToken = getValidToken() ?: ""
        if (activeToken.isBlank()) {
            return@withContext Result.success(Unit)
        }

        try {
            val siteUrl = getSiteUrl().removeSuffix("/")
            val chitietId = getListChiTietId()
            val url = "$siteUrl/_api/web/lists(guid'$chitietId')/items($taskId)"
            var curTok = activeToken
            var digest = getFormDigest(siteUrl, curTok)

            var req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $curTok")
                .header("X-HTTP-Method", "DELETE")
                .header("If-Match", "*")
            if (!digest.isNullOrBlank()) req.header("X-RequestDigest", digest)
            var resp = httpClient.newCall(req.post("".toRequestBody(null)).build()).execute()

            if (resp.code == 401) {
                resp.close()
                val refreshed = forceRefreshToken()
                if (refreshed != null) {
                    curTok = refreshed
                    digest = getFormDigest(siteUrl, curTok)
                    req = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $curTok")
                        .header("X-HTTP-Method", "DELETE")
                        .header("If-Match", "*")
                    if (!digest.isNullOrBlank()) req.header("X-RequestDigest", digest)
                    resp = httpClient.newCall(req.post("".toRequestBody(null)).build()).execute()
                }
            }

            resp.use { r ->
                if (r.isSuccessful || r.code == 200 || r.code == 204) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("HTTP ${r.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun recalculateParentProgress(parentId: Long?, currentTasks: List<TaskItem>) {
        if (parentId == null || parentId <= 0L) return
        val currentChinh = _taskChinhList.value
        val parent = currentChinh.find { it.id == parentId } ?: return
        val relatedSubs = currentTasks.filter { it.taskChinhId == parentId }
        val newProgress = parent.computedProgress(relatedSubs)
        val newStatus = when {
            relatedSubs.isNotEmpty() && relatedSubs.all { it.isCompleted } -> "Hoàn thành"
            relatedSubs.any { it.isCompleted } || newProgress > 0 -> "Đang làm"
            else -> if (parent.safeTrangThai.contains("Hoàn thành", ignoreCase = true)) "Đang làm" else parent.safeTrangThai
        }
        val updatedChinh = currentChinh.map {
            if (it.id == parentId) it.copy(progress = newProgress, trangThai = newStatus) else it
        }
        saveTaskChinh(updatedChinh)
    }

    fun resetToFallback() {
        saveTasks(emptyList())
        saveTaskChinh(emptyList())
        saveUsers(getFallbackUsers())
    }

    fun getFallbackUsers(): List<SeaZenUser> {
        return listOf(
            SeaZenUser(26, "ĐẶNG THÀNH NGUYÊN", "nguyen.dang@seazen.com.vn", "Phó Giám Đốc", "Vận Hành"),
            SeaZenUser(50, "Đỗ Nguyễn Hạnh Dung", "dung.do@seazen.com.vn", "Trưởng Phòng", "Kinh Doanh"),
            SeaZenUser(33, "ĐỖ THỊ HƯƠNG", "huong.do@seazen.com.vn", "Trưởng Bộ Phận", "C&B"),
            SeaZenUser(36, "ĐỖ VĂN TOÀN", "toan.do@seazen.com.vn", "Tổng Giám Đốc", "Tổng Giám Đốc"),
            SeaZenUser(35, "ĐỖ XUÂN THẮNG", "thang.do@seazen.com.vn", "Admin", "Admin"),
            SeaZenUser(38, "DƯ THỤC UYÊN", "uyen.du@seazen.com.vn", "Nhân Viên", "Kế Toán"),
            SeaZenUser(34, "DƯƠNG VĂN NAM", "nam.duong@seazen.com.vn", "Trưởng Bộ Phận", "Vận Hành"),
            SeaZenUser(49, "Giang Test", "giang.vo1@seazen.com.vn", "Giám Đốc", "SeaZen Tech"),
            SeaZenUser(43, "HÀ QUANG HIỆU", "hieu.ha@seazen.com.vn", "Nhân Viên", "SRT"),
            SeaZenUser(46, "HỒ HOÀNG SANG", "sang.ho@seazen.com.vn", "Nhân Viên", "SeaZen Tech"),
            SeaZenUser(29, "HOÀNG HỮU PHÚC", "phuc.hoang@seazen.com.vn", "Trưởng Bộ Phận", "Tuyển Dụng"),
            SeaZenUser(30, "HỨA KIM TÀI", "tai.hua@seazen.com.vn", "Trưởng Bộ Phận", "Vận Hành"),
            SeaZenUser(25, "HUỲNH NGỌC CHÂU", "chau.huynh@seazen.com.vn", "Chuyên Viên", "Pháp Lý"),
            SeaZenUser(41, "KHƯƠNG VĂN MINH", "minh.khuong@seazen.com.vn", "Phó Phòng", "Kinh Doanh"),
            SeaZenUser(20, "LÂM HỮU PHƯỚC", "phuoc.lam@seazen.com.vn", "Chuyên Viên", "Marketing"),
            SeaZenUser(37, "LÊ HOÀI THẢO", "thao.le@seazen.com.vn", "Trưởng Phòng", "Kế Toán"),
            SeaZenUser(48, "LÊ VĂN HỒNG", "hong.le@seazen.com.vn", "Giám Đốc", "HCNS"),
            SeaZenUser(23, "NGUYỄN ĐÌNH HOÀNG", "hoang.nguyen@seazen.com.vn", "Chuyên Viên", "Đào Tạo"),
            SeaZenUser(44, "NGUYỄN HẢI MINH", "minh.nguyen@seazen.com.vn", "Chuyên Viên", "SRT"),
            SeaZenUser(45, "NGUYỄN HUỲNH VĂN SANG", "sang.nguyen@seazen.com.vn", "Nhân Viên", "SeaZen Tech"),
            SeaZenUser(17, "NGUYỄN THỊ BÍNH", "binh.nguyen@seazen.com.vn", "Chuyên Viên", "Tuyển Dụng"),
            SeaZenUser(39, "NGUYỄN THỊ CẨM HẠNH", "hanh.nguyen@seazen.com.vn", "Chuyên Viên", "Kế Toán"),
            SeaZenUser(42, "NGUYỄN THỊ LAN ANH", "anh.nguyen@seazen.com.vn", "Trưởng Bộ Phận", "Hành Chính"),
            SeaZenUser(4, "NGUYỄN THỊ NGÂN HÀ", "ha.nguyen@seazen.com.vn", "Trưởng Phòng", "Đào Tạo"),
            SeaZenUser(51, "Nguyễn Thị Ngọc Minh", "minh.nguyen1@seazen.com.vn", "Giám Đốc", "Kinh Doanh"),
            SeaZenUser(40, "NGUYỄN TRƯƠNG MINH ANH", "anh.nguyen1@seazen.com.vn", "Nhân Viên", "Kinh Doanh"),
            SeaZenUser(19, "NGUYỄN VĂN THANH", "thanh.nguyen@seazen.com.vn", "Trưởng Bộ Phận", "Vận Hành"),
            SeaZenUser(31, "PHAN MINH TÀI", "tai.phan@seazen.com.vn", "Admin", "Admin"),
            SeaZenUser(22, "PHÙNG THỊ THẠNH", "thanh.phung@seazen.com.vn", "Chuyên Viên", "C&B"),
            SeaZenUser(47, "test", "powerapp@seazenvn.onmicrosoft.com", "Nhân Viên", "CHĐ"),
            SeaZenUser(28, "TRẦN GIA KHIÊM", "khiem.tran@seazen.com.vn", "Phó Phòng", "Marketing"),
            SeaZenUser(24, "TRỊNH HOÀNG THÁM", "tham.trinh@seazen.com.vn", "Trưởng Bộ Phận", "Vận Hành"),
            SeaZenUser(32, "TRỊNH THÁI THUẬN", "thuan.trinh@seazen.com.vn", "Chuyên Viên", "SeaZen Tech"),
            SeaZenUser(1, "Võ Nguyễn Trường Giang", "giang.vo@seazen.com.vn", "Admin", "Admin")
        )
    }
}
