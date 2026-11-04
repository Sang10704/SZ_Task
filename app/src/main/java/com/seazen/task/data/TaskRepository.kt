package com.seazen.task.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.seazen.task.widget.SeaZenWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class TaskRepository(private val context: Context) {
    companion object {
        @Volatile
        private var instance: TaskRepository? = null

        fun getInstance(context: Context): TaskRepository {
            return instance ?: synchronized(this) {
                instance ?: TaskRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences("seazen_task_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val fileLock = Any()
    private val tokenMutex = Mutex()
    private val syncMutex = Mutex()

    fun isNetworkAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    // Danh sach Task Chi Tiet (Viec con)
    private val _tasks = MutableStateFlow<List<TaskItem>>(emptyList())
    val tasks: StateFlow<List<TaskItem>> = _tasks.asStateFlow()

    // Danh sach Task Chinh (Du an / Muc tieu lon)
    private val _taskChinhList = MutableStateFlow<List<TaskChinhItem>>(emptyList())
    val taskChinhList: StateFlow<List<TaskChinhItem>> = _taskChinhList.asStateFlow()

    // Danh sach Nhan su phan quyen (DanhSachPhanQuyen)
    private val _users = MutableStateFlow<List<SeaZenUser>>(emptyList())
    val users: StateFlow<List<SeaZenUser>> = _users.asStateFlow()

    // Trang thai dong bo SharePoint truc tiep
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // Thong tin cap nhat phien ban OTA tu POWERAPP LATEST VERSION
    private val _appUpdateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val appUpdateInfo: StateFlow<AppUpdateInfo?> = _appUpdateInfo.asStateFlow()

    init {
        loadCachedData()
    }

    private fun getCacheFile(filename: String): File {
        return File(context.filesDir, filename)
    }

    private fun <T> saveListToFile(filename: String, list: List<T>) {
        synchronized(fileLock) {
            try {
                val file = getCacheFile(filename)
                val tempFile = File(context.filesDir, "$filename.tmp")
                BufferedWriter(FileWriter(tempFile)).use { writer ->
                    gson.toJson(list, writer)
                }
                if (tempFile.exists()) {
                    if (file.exists()) file.delete()
                    tempFile.renameTo(file)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun <T> loadListFromFile(filename: String, type: java.lang.reflect.Type): List<T>? {
        synchronized(fileLock) {
            return try {
                val file = getCacheFile(filename)
                if (!file.exists()) null
                else {
                    BufferedReader(FileReader(file)).use { reader ->
                        gson.fromJson<List<T>>(reader, type)
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    fun loadCachedData() {
        try {
            val taskType = object : TypeToken<List<TaskItem>>() {}.type
            val fromFile = loadListFromFile<TaskItem>("tasks_cache.json", taskType)
            if (!fromFile.isNullOrEmpty()) {
                _tasks.value = fromFile.filterNotNull()
            } else {
                val rawTasks = prefs.getString("tasks_json", null)
                if (!rawTasks.isNullOrBlank()) {
                    val parsed: List<TaskItem>? = gson.fromJson(rawTasks, taskType)
                    val list = parsed?.filterNotNull() ?: emptyList()
                    if (list.isNotEmpty()) {
                        _tasks.value = list
                        saveListToFile("tasks_cache.json", list)
                    }
                } else {
                    _tasks.value = emptyList()
                }
            }
        } catch (_: Exception) {
            _tasks.value = emptyList()
        }

        try {
            val chinhType = object : TypeToken<List<TaskChinhItem>>() {}.type
            val fromFile = loadListFromFile<TaskChinhItem>("task_chinh_cache.json", chinhType)
            if (!fromFile.isNullOrEmpty()) {
                _taskChinhList.value = fromFile.filterNotNull()
            } else {
                val rawChinh = prefs.getString("task_chinh_json", null)
                if (!rawChinh.isNullOrBlank()) {
                    val parsed: List<TaskChinhItem>? = gson.fromJson(rawChinh, chinhType)
                    val list = parsed?.filterNotNull() ?: emptyList()
                    if (list.isNotEmpty()) {
                        _taskChinhList.value = list
                        saveListToFile("task_chinh_cache.json", list)
                    }
                } else {
                    _taskChinhList.value = emptyList()
                }
            }
        } catch (_: Exception) {
            _taskChinhList.value = emptyList()
        }

        try {
            val userType = object : TypeToken<List<SeaZenUser>>() {}.type
            val fromFile = loadListFromFile<SeaZenUser>("users_cache.json", userType)
            if (!fromFile.isNullOrEmpty()) {
                _users.value = fromFile.filterNotNull()
            } else {
                val rawUsers = prefs.getString("users_json", null)
                if (!rawUsers.isNullOrBlank()) {
                    val parsed: List<SeaZenUser>? = gson.fromJson(rawUsers, userType)
                    _users.value = parsed?.filterNotNull() ?: emptyList()
                } else {
                    _users.value = emptyList()
                }
            }
        } catch (_: Exception) {
            _users.value = emptyList()
        }
    }

    fun saveTasks(newTasks: List<TaskItem>) {
        _tasks.value = newTasks
        saveListToFile("tasks_cache.json", newTasks)
        try {
            if (newTasks.size <= 500) {
                prefs.edit().putString("tasks_json", gson.toJson(newTasks)).apply()
            } else {
                prefs.edit().remove("tasks_json").apply()
            }
        } catch (_: Exception) {}
        notifyWidgetUpdate()
    }

    fun saveTaskChinh(newList: List<TaskChinhItem>) {
        _taskChinhList.value = newList
        saveListToFile("task_chinh_cache.json", newList)
        try {
            if (newList.size <= 500) {
                prefs.edit().putString("task_chinh_json", gson.toJson(newList)).apply()
            } else {
                prefs.edit().remove("task_chinh_json").apply()
            }
        } catch (_: Exception) {}
        notifyWidgetUpdate()
    }

    fun saveUsers(newUsers: List<SeaZenUser>) {
        _users.value = newUsers
        saveListToFile("users_cache.json", newUsers)
        prefs.edit().putString("users_json", gson.toJson(newUsers)).apply()
    }

    fun notifyWidgetUpdate() {
        try {
            SeaZenWidgetProvider.notifyAllWidgets(context)
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

    suspend fun getValidToken(): String? = tokenMutex.withLock {
        val currentToken = getSavedToken()
        if (currentToken.isNotBlank() && !isTokenExpired(currentToken)) {
            return@withLock currentToken
        }
        val rt = getSavedRefreshToken()
        if (rt.isNotBlank()) {
            val refreshRes = authManager.refreshAccessToken(rt)
            if (refreshRes.success && refreshRes.accessToken != null) {
                saveToken(refreshRes.accessToken)
                if (refreshRes.refreshToken != null) {
                    saveRefreshToken(refreshRes.refreshToken)
                }
                return@withLock refreshRes.accessToken
            }
        }
        return@withLock currentToken.ifBlank { null }
    }

    suspend fun forceRefreshToken(): String? = tokenMutex.withLock {
        val rt = getSavedRefreshToken()
        if (rt.isBlank()) return@withLock null
        val refreshRes = authManager.refreshAccessToken(rt)
        return@withLock if (refreshRes.success && refreshRes.accessToken != null) {
            saveToken(refreshRes.accessToken)
            if (refreshRes.refreshToken != null) {
                saveRefreshToken(refreshRes.refreshToken)
            }
            refreshRes.accessToken
        } else {
            null
        }
    }

    fun extractUserFromToken(token: String): Pair<String?, String?> {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return Pair(null, null)
            val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP))
            val json = gson.fromJson(payload, JsonObject::class.java)
            val email = json.get("upn")?.asString
                ?: json.get("unique_name")?.asString
                ?: json.get("email")?.asString
                ?: json.get("preferred_username")?.asString
            val name = json.get("name")?.asString
            Pair(email, name)
        } catch (_: Exception) {
            Pair(null, null)
        }
    }

    suspend fun fetchCurrentUserFromSharePoint(siteUrl: String, token: String): Pair<String?, String?> = withContext(Dispatchers.IO) {
        try {
            val url = "$siteUrl/_api/web/currentuser?%24select=Id,Title,Email,UserPrincipalName,LoginName"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json;odata=verbose")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: "{}"
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val d = json.getAsJsonObject("d")
                    val title = d?.get("Title")?.asString
                    var email = d?.get("Email")?.asString?.takeIf { it.isNotBlank() }
                    if (email.isNullOrBlank()) {
                        email = d?.get("UserPrincipalName")?.asString?.takeIf { it.isNotBlank() }
                    }
                    if (email.isNullOrBlank()) {
                        val login = d?.get("LoginName")?.asString ?: ""
                        if (login.contains("membership|")) {
                            email = login.substringAfter("membership|")
                        }
                    }
                    Pair(email, title)
                } else Pair(null, null)
            }
        } catch (_: Exception) {
            Pair(null, null)
        }
    }

    suspend fun syncCurrentUserIdentity(token: String? = null, enteredEmail: String? = null) = withContext(Dispatchers.IO) {
        val activeToken = token?.trim()?.ifBlank { null } ?: getValidToken() ?: ""
        var resolvedEmail: String? = null
        var resolvedName: String? = null

        if (activeToken.isNotBlank()) {
            val (spEmail, spName) = fetchCurrentUserFromSharePoint(getSiteUrl().removeSuffix("/"), activeToken)
            if (!spEmail.isNullOrBlank()) resolvedEmail = spEmail
            if (!spName.isNullOrBlank()) resolvedName = spName
        }

        if (resolvedEmail.isNullOrBlank() && activeToken.isNotBlank()) {
            val (jwtEmail, jwtName) = extractUserFromToken(activeToken)
            if (!jwtEmail.isNullOrBlank()) resolvedEmail = jwtEmail
            if (!jwtName.isNullOrBlank() && resolvedName.isNullOrBlank()) resolvedName = jwtName
        }

        if (resolvedEmail.isNullOrBlank() && !enteredEmail.isNullOrBlank()) {
            resolvedEmail = enteredEmail.trim()
        }

        if (!resolvedEmail.isNullOrBlank()) {
            saveEmail(resolvedEmail)
            val usersList = _users.value
            val foundUser = usersList.find { it.email.equals(resolvedEmail, ignoreCase = true) }
            if (foundUser != null && foundUser.name.isNotBlank()) {
                saveUserName(foundUser.name)
            } else if (!resolvedName.isNullOrBlank()) {
                saveUserName(resolvedName)
            }
        }
    }

    fun getSavedEmail(): String = prefs.getString("user_email", "") ?: ""
    fun saveEmail(email: String) = prefs.edit().putString("user_email", email.trim()).apply()
    fun getSavedUserName(): String = prefs.getString("user_name", "") ?: ""
    fun saveUserName(name: String) = prefs.edit().putString("user_name", name.trim()).apply()

    fun getSiteUrl(): String = prefs.getString("sp_site_url", "https://seazenvn.sharepoint.com/sites/ONESEAZEN") ?: "https://seazenvn.sharepoint.com/sites/ONESEAZEN"
    fun getListChiTietId(): String = prefs.getString("sp_list_chitiet_id", "fd83aa4a-a642-43c2-b2f0-d34079e850de") ?: "fd83aa4a-a642-43c2-b2f0-d34079e850de"
    fun getListChinhId(): String = prefs.getString("sp_list_chinh_id", "4d195cd8-53a0-472e-bdec-5a75c7062c14") ?: "4d195cd8-53a0-472e-bdec-5a75c7062c14"
    fun getListPhanQuyenId(): String {
        val id = prefs.getString("sp_list_phanquyen_id", null)
        if (id.isNullOrBlank() || id == "f2a58b54-94c3-4c91-b3b3-f2223a496813" || id == "bad6b67b-9202-480b-8c9d-4d6f8b288625") {
            prefs.edit().putString("sp_list_phanquyen_id", "f7f3a310-a824-4ba9-a673-bf0038a423b9").apply()
            return "f7f3a310-a824-4ba9-a673-bf0038a423b9"
        }
        return id
    }

    fun logout() {
        prefs.edit()
            .remove("ms_token")
            .remove("ms_refresh_token")
            .remove("user_email")
            .remove("user_name")
            .apply()
        _tasks.value = emptyList()
        _taskChinhList.value = emptyList()
        try {
            clearLocalCache()
            SeaZenWidgetProvider.notifyAllWidgets(context)
        } catch (_: Exception) {}
    }

    fun getListVersionId(): String = "af8925dc-0808-4a6a-bef0-4c1a9dec5179"

    fun getCurrentAppVersion(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.9.2"
        } catch (_: Exception) {
            "1.9.2"
        }
    }

    fun isNewerVersion(serverVer: String, currentVer: String): Boolean {
        try {
            val cleanServer = serverVer.lowercase().replace("version", "").replace("v.", "").replace("v", "").trim()
            val cleanCurrent = currentVer.lowercase().replace("version", "").replace("v.", "").replace("v", "").trim()

            if (cleanServer.isBlank() || cleanCurrent.isBlank()) return false
            if (cleanServer == cleanCurrent) return false

            val sParts = cleanServer.split(".").mapNotNull { it.toIntOrNull() }
            val cParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }

            val maxLen = maxOf(sParts.size, cParts.size)
            for (i in 0 until maxLen) {
                val s = sParts.getOrElse(i) { 0 }
                val c = cParts.getOrElse(i) { 0 }
                if (s > c) return true
                if (s < c) {
                    // Xu ly chong nham lan tien to 0.9.x va 1.9.x (vi du server cau hinh 0.9.2 nhung app la 1.9.1)
                    if (i == 0 && (s == 0 && c == 1 || s == 1 && c == 0) && sParts.size >= 3 && cParts.size >= 3 && sParts[1] == cParts[1]) {
                        val sPatch = sParts[2]
                        val cPatch = cParts[2]
                        if (sPatch > cPatch) return true
                    }
                    return false
                }
            }
            return false
        } catch (_: Exception) {
            return false
        }
    }

    suspend fun checkForAppUpdate(token: String? = null): AppUpdateInfo? = withContext(Dispatchers.IO) {
        var activeToken = token?.trim()?.ifBlank { null } ?: getValidToken() ?: ""
        if (activeToken.isBlank()) return@withContext null

        try {
            val siteUrl = getSiteUrl().removeSuffix("/")
            val listId = getListVersionId()
            val url = "$siteUrl/_api/web/lists(guid'$listId')/items?%24select=ID,Title,AppName,LatestVersion,DownloadUrl,N_x1ed9_idungc_x1ead_pnh_x1ead_t"

            var req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $activeToken")
                .header("Accept", "application/json;odata=verbose")
                .header("Cache-Control", "no-cache, no-store")
                .build()

            var resp = httpClient.newCall(req).execute()
            if (resp.code == 401) {
                resp.close()
                val refreshed = forceRefreshToken()
                if (refreshed != null) {
                    activeToken = refreshed
                    req = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $activeToken")
                        .header("Accept", "application/json;odata=verbose")
                        .header("Cache-Control", "no-cache, no-store")
                        .build()
                    resp = httpClient.newCall(req).execute()
                }
            }

            resp.use { r ->
                if (r.isSuccessful) {
                    val body = r.body?.string() ?: "{}"
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val results = json.getAsJsonObject("d")?.getAsJsonArray("results") ?: return@withContext null

                    for (elem in results) {
                        val it = elem.asJsonObject
                        val appName = (
                            it.get("AppName")?.takeIf { !it.isJsonNull }?.asString
                                ?: it.get("Title")?.takeIf { !it.isJsonNull }?.asString
                                ?: ""
                        ).trim().uppercase()
                        if (appName == "SZ_TASK" || appName == "SZTASK" || appName == "SZ-TASK") {
                            val latestVer = it.get("LatestVersion")?.takeIf { !it.isJsonNull }?.asString?.trim() ?: ""
                            val dlUrl = it.get("DownloadUrl")?.takeIf { !it.isJsonNull }?.asString?.trim()?.ifBlank { null }
                                ?: "https://github.com/Sang10704/SZ_Task/releases/download/Sz_Task/SZ_Task.apk"
                            val notes = it.get("N_x1ed9_idungc_x1ead_pnh_x1ead_t")?.takeIf { !it.isJsonNull }?.asString?.trim() ?: ""

                            val currentVer = getCurrentAppVersion()
                            val hasUpdate = isNewerVersion(latestVer, currentVer)

                            val info = AppUpdateInfo(
                                latestVersion = latestVer,
                                downloadUrl = dlUrl,
                                notes = notes,
                                hasUpdate = hasUpdate
                            )
                            _appUpdateInfo.value = info
                            return@withContext info
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun isConfigured(): Boolean = getSavedToken().isNotBlank() || getSavedRefreshToken().isNotBlank()

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

    private fun resolveCleanUserName(rawName: String, email: String, userByEmailMap: Map<String, SeaZenUser>): String {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isNotBlank()) {
            val matched = userByEmailMap[cleanEmail]
            if (matched != null && matched.name.isNotBlank()) {
                return matched.name
            }
        }
        return rawName.trim()
    }

    suspend fun fetchAllFromSharePoint(token: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            _isSyncing.value = true
            try {
                var activeToken = token?.trim()?.ifBlank { null } ?: getValidToken() ?: ""
                if (activeToken.isBlank()) {
                    return@withLock Result.failure(Exception("Chưa có phiên làm việc."))
                }

                val siteUrl = getSiteUrl().removeSuffix("/")
                val chitietId = getListChiTietId()
                val chinhId = getListChinhId()
                val phanquyenId = getListPhanQuyenId()

                // Goi song song ca 4 endpoint truc tiep tu SharePoint
                coroutineScope {
                    val defUpdate = async { checkForAppUpdate(activeToken) }
                    val defChinh = async { fetchRawChinh(siteUrl, chinhId, activeToken) }
                    val defChiTiet = async { fetchRawChiTiet(siteUrl, chitietId, activeToken) }
                    val defUsers = async { fetchRawUsers(siteUrl, phanquyenId, activeToken) }

                    val serverUsers = defUsers.await()
                    if (serverUsers.isNotEmpty()) {
                        saveUsers(serverUsers)
                    }
                    val activeUsers = if (serverUsers.isNotEmpty()) serverUsers else _users.value
                    val userByEmailMap = activeUsers
                        .filter { it.email.isNotBlank() }
                        .associateBy { it.email.trim().lowercase() }

                    val (rawServerChinh, newTok1) = defChinh.await()
                    if (newTok1 != null) activeToken = newTok1

                    val (rawServerChiTiet, newTok2) = defChiTiet.await()
                    if (newTok2 != null) activeToken = newTok2

                    // Chuan hoa ho ten tu DanhSachPhanQuyen cho TaskChinh
                    val enrichedChinh = rawServerChinh.map { p ->
                        val resolvedRespName = resolveCleanUserName(p.safeResponsiblePerson, p.safeResponsibleEmail, userByEmailMap)
                        val resolvedCreatorName = resolveCleanUserName(p.safeCreatorName, p.safeCreatorEmail, userByEmailMap)
                        p.copy(
                            responsiblePerson = resolvedRespName,
                            creatorName = resolvedCreatorName
                        )
                    }

                    val chinhByIdMap = enrichedChinh.associateBy { it.id }
                    val chinhByTitleMap = enrichedChinh
                        .filter { it.safeTitle.isNotBlank() }
                        .associateBy { it.safeTitle.trim().lowercase() }

                    // Chuan hoa TaskChiTiet: Dien day du TaskChinhID, TaskChinhTitle, PhongBan va TenNguoiThucHien
                    val enrichedChiTiet = rawServerChiTiet.map { t ->
                        val parentProj = if (t.taskChinhId != null) {
                            chinhByIdMap[t.taskChinhId]
                        } else if (t.safeTaskChinhTitle.isNotBlank()) {
                            chinhByTitleMap[t.safeTaskChinhTitle.trim().lowercase()]
                        } else null

                        val resolvedAssigneeName = resolveCleanUserName(t.safeAssigneeName, t.safeAssigneeEmail, userByEmailMap)
                        t.copy(
                            taskChinhId = t.taskChinhId ?: parentProj?.id,
                            taskChinhTitle = t.safeTaskChinhTitle.ifBlank { parentProj?.safeTitle ?: "" },
                            phongBan = t.safePhongBan.ifBlank { parentProj?.safePhongBan ?: "" },
                            assigneeName = resolvedAssigneeName
                        )
                    }

                    // SINGLE SOURCE OF TRUTH (SSOT): Tinh toan tien do & trang thai thuc te tu cac viec con
                    val subsByParentId = enrichedChiTiet.groupBy { it.taskChinhId }
                    val synchronizedChinh = enrichedChinh.map { p ->
                        val relatedSubs = subsByParentId[p.id] ?: emptyList()
                        if (relatedSubs.isNotEmpty()) {
                            val compProg = p.computedProgress(relatedSubs)
                            val compStatus = when {
                                relatedSubs.all { it.isCompleted } -> "Hoàn thành"
                                compProg > 0 -> "Đang thực hiện"
                                else -> p.safeTrangThai
                            }
                            p.copy(progress = compProg, trangThai = compStatus)
                        } else p
                    }

                    saveTaskChinh(synchronizedChinh)
                    saveTasks(enrichedChiTiet)

                    // Dong bo dinh danh tai khoan hien tai
                    var currentSavedEmail = getSavedEmail()
                    if (currentSavedEmail.isBlank()) {
                        syncCurrentUserIdentity(activeToken)
                        currentSavedEmail = getSavedEmail()
                    }
                    if (currentSavedEmail.isNotBlank()) {
                        val matched = userByEmailMap[currentSavedEmail.trim().lowercase()]
                        if (matched != null && matched.name.isNotBlank()) {
                            saveUserName(matched.name)
                        }
                    }

                    defUpdate.await()
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private suspend fun fetchRawChinh(siteUrl: String, chinhId: String, token: String): Pair<List<TaskChinhItem>, String?> {
        var activeToken = token
        var newTok: String? = null
        var currentUrl: String? = "$siteUrl/_api/web/lists(guid'$chinhId')/items?%24top=1000&%24select=ID,Title,TenNguoiChiuTrachNhiem,NguoiChiuTrachNhiemEmail,NguoiChiuTrachNhiem/Title,NguoiChiuTrachNhiem/EMail,NgayBatDau,NgayKetThuc,TienDo,PhongBan,PhanLoaiTask,PhanLoaiViec,TrangThai,TenNguoiGiaoViec,NguoiGiaoViecEmail,NguoiGiaoViec/Title,NguoiGiaoViec/EMail,TenNguoiNhanThongBao,NguoiNhanThongBaoEmail,NgayHanMoi,LyDoDoiHan,TrangThaiDuyetHan&%24expand=NguoiChiuTrachNhiem,NguoiGiaoViec&%24orderby=ID%20desc"
        val list = mutableListOf<TaskChinhItem>()

        while (!currentUrl.isNullOrBlank() && list.size < 50000) {
            var req = Request.Builder()
                .url(currentUrl!!)
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
                        .url(currentUrl!!)
                        .header("Authorization", "Bearer $activeToken")
                        .header("Accept", "application/json;odata=verbose")
                        .build()
                    resp = httpClient.newCall(req).execute()
                }
            }

            var nextLink: String? = null
            resp.use { r ->
                if (!r.isSuccessful) {
                    throw java.io.IOException("SharePoint Chinh error HTTP ${r.code}: ${r.message}")
                }
                val bodyStr = r.body?.string() ?: "{}"
                val jsonRoot = gson.fromJson(bodyStr, JsonObject::class.java)
                val dObj = jsonRoot.getAsJsonObject("d")
                val results = dObj?.getAsJsonArray("results") ?: JsonArray()
                for (el in results) {
                    if (el !is JsonObject) continue
                    val pb = el.get("PhongBan")?.takeIf { !it.isJsonNull }?.asString ?: "SeaZen Tech"
                    val pl = el.get("PhanLoaiTask")?.takeIf { !it.isJsonNull }?.asString ?: "Phòng ban"
                    val plv = el.get("PhanLoaiViec")?.takeIf { !it.isJsonNull }?.asString ?: "Công việc tháng"
                    val tt = el.get("TrangThai")?.takeIf { !it.isJsonNull }?.asString ?: "Đang thực hiện"
                    val rawProg = el.get("TienDo")?.takeIf { !it.isJsonNull }?.asDouble
                    val progVal = when {
                        rawProg == null -> 0
                        rawProg <= 1.0 -> (rawProg * 100).toInt().coerceIn(0, 100)
                        else -> rawProg.toInt().coerceIn(0, 100)
                    }

                    // Ho tro dong thoi cot Text va cot Person/Group (NguoiChiuTrachNhiem, NguoiGiaoViec)
                    val respObj = el.get("NguoiChiuTrachNhiem")?.takeIf { it.isJsonObject }?.asJsonObject
                    val respPerson = el.get("TenNguoiChiuTrachNhiem")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
                        ?: respObj?.get("Title")?.takeIf { !it.isJsonNull }?.asString ?: ""
                    val respEmail = el.get("NguoiChiuTrachNhiemEmail")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
                        ?: respObj?.get("EMail")?.takeIf { !it.isJsonNull }?.asString ?: ""

                    val creatorObj = el.get("NguoiGiaoViec")?.takeIf { it.isJsonObject }?.asJsonObject
                    val creatorPerson = el.get("TenNguoiGiaoViec")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
                        ?: creatorObj?.get("Title")?.takeIf { !it.isJsonNull }?.asString ?: ""
                    val creatorMail = el.get("NguoiGiaoViecEmail")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
                        ?: creatorObj?.get("EMail")?.takeIf { !it.isJsonNull }?.asString ?: ""

                    list.add(
                        TaskChinhItem(
                            id = el.get("ID")?.asLong ?: 0L,
                            title = el.get("Title")?.takeIf { !it.isJsonNull }?.asString ?: "",
                            responsiblePerson = respPerson,
                            responsibleEmail = respEmail,
                            startDate = el.get("NgayBatDau")?.takeIf { !it.isJsonNull }?.asString ?: "",
                            endDate = el.get("NgayKetThuc")?.takeIf { !it.isJsonNull }?.asString ?: "",
                            progress = progVal,
                            phongBan = pb,
                            phanLoaiTask = pl,
                            phanLoaiViec = plv,
                            trangThai = tt,
                            creatorName = creatorPerson,
                            creatorEmail = creatorMail,
                            ccName = el.get("TenNguoiNhanThongBao")?.takeIf { !it.isJsonNull }?.asString ?: "",
                            ccEmail = el.get("NguoiNhanThongBaoEmail")?.takeIf { !it.isJsonNull }?.asString ?: "",
                            newEndDate = el.get("NgayHanMoi")?.takeIf { !it.isJsonNull }?.asString ?: "",
                            extReason = el.get("LyDoDoiHan")?.takeIf { !it.isJsonNull }?.asString ?: "",
                            trangThaiDuyetHan = el.get("TrangThaiDuyetHan")?.takeIf { !it.isJsonNull }?.asString ?: "",
                            ghiChu = ""
                        )
                    )
                }
                nextLink = dObj?.get("__next")?.takeIf { !it.isJsonNull }?.asString
            }
            currentUrl = nextLink
        }
        return Pair(list, newTok)
    }

    private suspend fun fetchRawChiTiet(siteUrl: String, chitietId: String, token: String): Pair<List<TaskItem>, String?> {
        var activeToken = token
        var newTok: String? = null
        var currentUrl: String? = "$siteUrl/_api/web/lists(guid'$chitietId')/items?%24top=1000&%24select=ID,Title,TaskChinhID/Id,TaskChinhID/Title,TaskChinhIDId,DaHoanThanh,TenNguoiThucHien,NguoiThucHienEmail,NguoiThucHien/Title,NguoiThucHien/EMail,NgayBatDau,NgayKetThuc,Created&%24expand=TaskChinhID,NguoiThucHien&%24orderby=ID%20desc"
        val list = mutableListOf<TaskItem>()

        while (!currentUrl.isNullOrBlank() && list.size < 50000) {
            var req = Request.Builder()
                .url(currentUrl!!)
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
                        .url(currentUrl!!)
                        .header("Authorization", "Bearer $activeToken")
                        .header("Accept", "application/json;odata=verbose")
                        .build()
                    resp = httpClient.newCall(req).execute()
                }
            }

            var nextLink: String? = null
            resp.use { r ->
                if (!r.isSuccessful) {
                    throw java.io.IOException("SharePoint ChiTiet error HTTP ${r.code}: ${r.message}")
                }
                val bodyStr = r.body?.string() ?: "{}"
                val jsonRoot = gson.fromJson(bodyStr, JsonObject::class.java)
                val dObj = jsonRoot.getAsJsonObject("d")
                val results = dObj?.getAsJsonArray("results") ?: JsonArray()
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

                    // Ho tro dong thoi cot Text va cot Person/Group (NguoiThucHien)
                    val assigneeObj = el.get("NguoiThucHien")?.takeIf { it.isJsonObject }?.asJsonObject
                    val assigneePerson = el.get("TenNguoiThucHien")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
                        ?: assigneeObj?.get("Title")?.takeIf { !it.isJsonNull }?.asString ?: ""
                    val assigneeMail = el.get("NguoiThucHienEmail")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
                        ?: assigneeObj?.get("EMail")?.takeIf { !it.isJsonNull }?.asString ?: ""

                    val prio = "normal"
                    list.add(
                        TaskItem(
                            id = el.get("ID")?.asLong ?: 0L,
                            title = el.get("Title")?.takeIf { !it.isJsonNull }?.asString ?: "Không tiêu đề",
                            taskChinhId = tcId,
                            taskChinhTitle = tcTitle,
                            phongBan = "",
                            isCompleted = el.get("DaHoanThanh")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
                            assigneeName = assigneePerson,
                            assigneeEmail = assigneeMail,
                            startDate = el.get("NgayBatDau")?.takeIf { !it.isJsonNull }?.asString ?: "",
                            endDate = el.get("NgayKetThuc")?.takeIf { !it.isJsonNull }?.asString ?: "",
                            priority = prio,
                            ghiChu = "",
                            created = el.get("Created")?.takeIf { !it.isJsonNull }?.asString ?: ""
                        )
                    )
                }
                nextLink = dObj?.get("__next")?.takeIf { !it.isJsonNull }?.asString
            }
            currentUrl = nextLink
        }
        return Pair(list, newTok)
    }

    private suspend fun fetchRawUsers(siteUrl: String, phanquyenId: String, token: String): List<SeaZenUser> {
        return try {
            var activeToken = token
            var currentUrl: String? = "$siteUrl/_api/web/lists(guid'$phanquyenId')/items?%24top=1000&%24select=ID,Title,Email,ChucVu,PhongBan,EmailNhanVien/Title,EmailNhanVien/EMail&%24expand=EmailNhanVien&%24orderby=Title%20asc"
            val list = mutableListOf<SeaZenUser>()

            while (!currentUrl.isNullOrBlank() && list.size < 5000) {
                var req = Request.Builder()
                    .url(currentUrl!!)
                    .header("Authorization", "Bearer $activeToken")
                    .header("Accept", "application/json;odata=verbose")
                    .build()
                var resp = httpClient.newCall(req).execute()

                if (resp.code == 401) {
                    resp.close()
                    val refreshed = forceRefreshToken()
                    if (refreshed != null) {
                        activeToken = refreshed
                        req = Request.Builder()
                            .url(currentUrl!!)
                            .header("Authorization", "Bearer $activeToken")
                            .header("Accept", "application/json;odata=verbose")
                            .build()
                        resp = httpClient.newCall(req).execute()
                    }
                }

                var nextLink: String? = null
                resp.use { r ->
                    if (r.isSuccessful) {
                        val bodyStr = r.body?.string() ?: "{}"
                        val jsonRoot = gson.fromJson(bodyStr, JsonObject::class.java)
                        val dObj = jsonRoot.getAsJsonObject("d")
                        val results = dObj?.getAsJsonArray("results") ?: JsonArray()
                        for (el in results) {
                            if (el !is JsonObject) continue
                            val uId = el.get("ID")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
                            val personObj = el.get("EmailNhanVien")?.takeIf { it.isJsonObject }?.asJsonObject
                            val uName = el.get("Title")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
                                ?: personObj?.get("Title")?.takeIf { !it.isJsonNull }?.asString ?: ""
                            val uEmail = el.get("Email")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
                                ?: personObj?.get("EMail")?.takeIf { !it.isJsonNull }?.asString ?: ""
                            val uChucVu = el.get("ChucVu")?.takeIf { !it.isJsonNull }?.asString ?: ""
                            val uPhongBan = el.get("PhongBan")?.takeIf { !it.isJsonNull }?.asString ?: ""
                            if (uName.isNotBlank() || uEmail.isNotBlank()) {
                                list.add(SeaZenUser(uId, uName.trim(), uEmail.trim(), uChucVu.trim(), uPhongBan.trim()))
                            }
                        }
                        nextLink = dObj?.get("__next")?.takeIf { !it.isJsonNull }?.asString
                    }
                }
                currentUrl = nextLink
            }
            list
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
        } catch (_: Exception) {
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
        val activeToken = getValidToken()
        if (activeToken.isNullOrBlank()) {
            return@withContext Result.failure(Exception("Chưa có phiên làm việc hoặc mất kết nối mạng. Vui lòng kết nối mạng để tạo công việc."))
        }

        try {
            val realItem = executeCreateTaskChinh(title, startDate, endDate, responsiblePerson, responsibleEmail, phongBan, phanLoaiTask, phanLoaiViec, creatorName, creatorEmail, ccName, ccEmail, activeToken)
            if (realItem != null) {
                val currentList = _taskChinhList.value.filter { it.id != realItem.id }
                saveTaskChinh(listOf(realItem) + currentList)
                Result.success(realItem)
            } else {
                Result.failure(Exception("Không thể tạo công việc chính trên SharePoint. Vui lòng thử lại."))
            }
        } catch (e: Exception) {
            Result.failure(e)
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
            if (endDate.isNotBlank()) addProperty("NgayKetThuc", if (endDate.length == 10) "${endDate}T00:00:00Z" else endDate)
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
        val parent = if (taskChinhId != null) _taskChinhList.value.find { it.id == taskChinhId } else null
        val resolvedParentTitle = if (taskChinhTitle.isNotBlank()) taskChinhTitle else (parent?.safeTitle ?: "")
        val resolvedPhongBan = parent?.safePhongBan ?: ""

        val activeToken = getValidToken()
        if (activeToken.isNullOrBlank()) {
            return@withContext Result.failure(Exception("Chưa có phiên làm việc hoặc mất kết nối mạng. Vui lòng kết nối mạng để tạo công việc."))
        }

        try {
            val realItem = executeCreateTaskChiTiet(
                title = title,
                taskChinhId = taskChinhId,
                taskChinhTitle = resolvedParentTitle,
                phongBan = resolvedPhongBan,
                startDate = startDate,
                endDate = endDate,
                assigneeName = assigneeName,
                assigneeEmail = assigneeEmail,
                token = activeToken
            )
            if (realItem != null) {
                val updatedTasks = listOf(realItem) + _tasks.value.filter { it.id != realItem.id }
                saveTasks(updatedTasks)
                if (taskChinhId != null) recalculateParentProgress(taskChinhId, updatedTasks)
                Result.success(realItem)
            } else {
                Result.failure(Exception("Không thể tạo công việc con trên SharePoint. Vui lòng thử lại."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun executeCreateTaskChiTiet(
        title: String,
        taskChinhId: Long?,
        taskChinhTitle: String,
        phongBan: String,
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
            if (endDate.isNotBlank()) addProperty("NgayKetThuc", if (endDate.length == 10) "${endDate}T00:00:00Z" else endDate)
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
                    phongBan = phongBan.trim()
                )
            } else null
        }
    }

    // Toggle hoan thanh TaskChiTiet
    suspend fun toggleTaskCompletion(taskId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        if (_tasks.value.isEmpty()) {
            loadCachedData()
        }
        val target = _tasks.value.firstOrNull { it.id == taskId }
            ?: return@withContext Result.failure(Exception("Không tìm thấy công việc."))

        val newDoneStatus = !target.isCompleted
        val activeToken = getValidToken()
        if (activeToken.isNullOrBlank()) {
            return@withContext Result.failure(Exception("Chưa có phiên làm việc. Vui lòng kết nối mạng để thao tác."))
        }

        try {
            val ok = executeToggleTaskCompletion(taskId, newDoneStatus, activeToken)
            if (ok) {
                val updated = _tasks.value.map {
                    if (it.id == taskId) it.copy(isCompleted = newDoneStatus) else it
                }
                saveTasks(updated)
                recalculateParentProgress(target.taskChinhId, updated)
                Result.success(newDoneStatus)
            } else {
                Result.failure(Exception("Không thể cập nhật trạng thái trên máy chủ SharePoint."))
            }
        } catch (e: Exception) {
            Result.failure(e)
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

        val activeToken = getValidToken()
        if (activeToken.isNullOrBlank()) {
            return@withContext Result.failure(Exception("Chưa có phiên làm việc hoặc mất kết nối mạng. Vui lòng kết nối mạng."))
        }

        try {
            val siteUrl = getSiteUrl().removeSuffix("/")
            val chinhId = getListChinhId()
            val url = "$siteUrl/_api/web/lists(guid'$chinhId')/items($taskId)"
            val cleanDate = newDate.trim()

            val jsonPayload = JsonObject().apply {
                val meta = JsonObject().apply {
                    addProperty("type", "SP.Data.TaskChinhListItem")
                }
                add("__metadata", meta)
                addProperty("NgayHanMoi", if (cleanDate.length == 10) "${cleanDate}T00:00:00Z" else cleanDate)
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
                    val updated = _taskChinhList.value.map {
                        if (it.id == target.id) it.copy(newEndDate = cleanDate, extReason = reason.trim(), trangThaiDuyetHan = "Chờ duyệt") else it
                    }
                    saveTaskChinh(updated)
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Không thể cập nhật trên SharePoint (HTTP ${r.code})"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Xoa TaskChiTiet
    suspend fun deleteTaskChiTiet(taskId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val activeToken = getValidToken()
        if (activeToken.isNullOrBlank()) {
            return@withContext Result.failure(Exception("Chưa có phiên làm việc. Vui lòng kết nối mạng."))
        }

        val target = _tasks.value.find { it.id == taskId }

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
                if (r.isSuccessful || r.code in listOf(200, 204, 404)) {
                    val updated = _tasks.value.filter { it.id != taskId }
                    saveTasks(updated)
                    if (target?.taskChinhId != null) {
                        recalculateParentProgress(target.taskChinhId, updated)
                    }
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Không thể xóa trên SharePoint (HTTP ${r.code})."))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun recalculateParentProgress(parentId: Long?, allTasks: List<TaskItem>) {
        if (parentId == null || parentId <= 0L) return
        val subs = allTasks.filter { it.taskChinhId == parentId }
        val targetParent = _taskChinhList.value.find { it.id == parentId } ?: return
        val newProg = if (subs.isEmpty()) 0 else targetParent.computedProgress(subs)
        val decimalProg = if (subs.isEmpty()) 0.0 else (subs.count { it.isCompleted }.toDouble() / subs.size.toDouble()).coerceIn(0.0, 1.0)
        val newStatus = when {
            subs.isNotEmpty() && subs.all { it.isCompleted } -> "Hoàn thành"
            newProg > 0 -> "Đang thực hiện"
            else -> "Chưa bắt đầu"
        }
        val updatedChinh = _taskChinhList.value.map {
            if (it.id == parentId) it.copy(progress = newProg, trangThai = newStatus) else it
        }
        saveTaskChinh(updatedChinh)

        // Dong bo TienDo va TrangThai cua TaskChinh len SharePoint de dong nhat voi PowerApps
        try {
            val activeToken = getValidToken()
            if (!activeToken.isNullOrBlank()) {
                syncParentProgressToSharePoint(parentId, decimalProg, newStatus, activeToken)
            }
        } catch (_: Exception) {}
    }

    private suspend fun syncParentProgressToSharePoint(
        parentId: Long,
        decimalProg: Double,
        status: String,
        token: String
    ) {
        val siteUrl = getSiteUrl().removeSuffix("/")
        val chinhId = getListChinhId()
        val url = "$siteUrl/_api/web/lists(guid'$chinhId')/items($parentId)"

        val jsonPayload = JsonObject().apply {
            val meta = JsonObject().apply {
                addProperty("type", "SP.Data.TaskChinhListItem")
            }
            add("__metadata", meta)
            addProperty("TienDo", decimalProg)
            addProperty("TrangThai", status)
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
        resp.close()
    }

    fun clearLocalCache() {
        _tasks.value = emptyList()
        _taskChinhList.value = emptyList()
        _users.value = emptyList()
        getCacheFile("tasks_cache.json").delete()
        getCacheFile("task_chinh_cache.json").delete()
        getCacheFile("users_cache.json").delete()
        prefs.edit().remove("tasks_json").remove("task_chinh_json").remove("users_json").apply()
        notifyWidgetUpdate()
    }
}
