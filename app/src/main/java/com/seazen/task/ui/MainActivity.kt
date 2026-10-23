package com.seazen.task.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seazen.task.data.SeaZenUser
import com.seazen.task.data.TaskChinhItem
import com.seazen.task.data.TaskItem
import com.seazen.task.data.TaskRepository
import com.seazen.task.data.parseLocalDate
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class MainActivity : ComponentActivity() {
    private val currentIntentState = mutableStateOf<Intent?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentIntentState.value = intent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentIntentState.value = intent

        val isCrashRecovery = intent.getBooleanExtra("crash_recovery", false)

        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            android.util.Log.e("SZTaskCrash", "Fatal exception", throwable)
            val prefs = getSharedPreferences("seazen_task_prefs", Context.MODE_PRIVATE)
            val detail = "${throwable.javaClass.simpleName}: ${throwable.message}\n" +
                    throwable.stackTrace.take(4).joinToString("\n") { "at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" }
            prefs.edit().putString("last_crash_error", detail).apply()

            val restartIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("crash_recovery", true)
            }
            startActivity(restartIntent)
            android.os.Process.killProcess(android.os.Process.myPid())
        }

        val repository = TaskRepository.getInstance(applicationContext)

        setContent {
            var appLang by remember { mutableStateOf(repository.getAppLanguage()) }

            CompositionLocalProvider(LocalAppLanguage provides appLang) {
                SZTaskTheme {
                    if (isCrashRecovery) {
                        val prefs = getSharedPreferences("seazen_task_prefs", Context.MODE_PRIVATE)
                        val errorMsg = prefs.getString("last_crash_error", "Đã phát hiện lỗi ứng dụng") ?: ""
                        CrashRecoveryScreen(
                            errorMessage = errorMsg,
                            onResetAndRestart = {
                                repository.resetToFallback()
                                prefs.edit().remove("last_crash_error").apply()
                                val restartIntent = Intent(this, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                                startActivity(restartIntent)
                                finish()
                            }
                        )
                    } else {
                        AppNavigator(
                            repository = repository,
                            intentState = currentIntentState,
                            appLang = appLang,
                            onSelectLang = {
                                appLang = it
                                repository.setAppLanguage(it)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            val repository = TaskRepository.getInstance(applicationContext)
            repository.notifyWidgetUpdate()
        } catch (_: Exception) {}
    }
}

@Composable
fun CrashRecoveryScreen(
    errorMessage: String,
    onResetAndRestart: () -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val str = AppStrings.of(lang)

    Scaffold(containerColor = BackgroundLight) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorderColor))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = str.crashRecoveryTitle,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFDC2626)
                    )
                    Text(
                        text = str.crashRecoveryDesc,
                        fontSize = 12.sp,
                        color = TextMuted
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = PillBg
                    ) {
                        Text(
                            text = errorMessage,
                            fontSize = 11.sp,
                            color = TextDark,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Button(
                        onClick = onResetAndRestart,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                    ) {
                        Text(str.actionRestoreRestart, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("error_log", errorMessage))
                            Toast.makeText(context, if (lang == "en") "Error details copied" else "Đã sao chép chi tiết lỗi", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(str.actionCopyCrashLog)
                    }
                }
            }
        }
    }
}

// Bảng màu Tím chuyên nghiệp trẻ trung theo thiết kế SZ Task
val PrimaryPurple = Color(0xFF5B4DF6)
val PrimaryPurpleDark = Color(0xFF4338CA)
val AccentPurpleLight = Color(0xFFEEF2FF)
val PrimaryDark = Color(0xFF0F172A)
val BackgroundLight = Color(0xFFFAFAFC)
val CardBorderColor = Color(0xFFF1F5F9)
val TextDark = Color(0xFF0F172A)
val TextMuted = Color(0xFF64748B)
val DotPink = Color(0xFFEC4899)
val DotCyan = Color(0xFF06B6D4)
val PillBg = Color(0xFFF1F5F9)

@Composable
fun SZTaskTheme(content: @Composable () -> Unit) {
    val colorScheme = lightColorScheme(
        primary = PrimaryPurple,
        onPrimary = Color.White,
        secondary = PrimaryPurpleDark,
        background = BackgroundLight,
        surface = Color.White,
        surfaceVariant = PillBg,
        outline = Color(0xFFE2E8F0)
    )
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

@Composable
fun AppNavigator(
    repository: TaskRepository,
    intentState: MutableState<Intent?>,
    appLang: String,
    onSelectLang: (String) -> Unit
) {
    var isLoggedIn by remember { mutableStateOf(repository.isConfigured()) }
    var showLanding by remember { mutableStateOf(!repository.isConfigured()) }

    if (isLoggedIn) {
        MainContainerScreen(
            repository = repository,
            intentState = intentState,
            appLang = appLang,
            onSelectLang = onSelectLang,
            onLogout = {
                repository.logout()
                isLoggedIn = false
                showLanding = true
            }
        )
    } else if (showLanding) {
        LandingScreen(
            onGetStarted = { showLanding = false }
        )
    } else {
        LoginScreen(
            repository = repository,
            onBackToLanding = { showLanding = true },
            onLoginSuccess = { isLoggedIn = true }
        )
    }
}

/**
 * Màn hình Container quản lý 4 Tab chính:
 * 0: Cá nhân (My Tasks)
 * 1: Phòng ban & Công ty (Departments)
 * 2: Lịch (Calendar)
 * 3: Cài đặt & Hồ sơ (Settings)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContainerScreen(
    repository: TaskRepository,
    intentState: MutableState<Intent?>,
    appLang: String,
    onSelectLang: (String) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(0) }
    var showCreateBottomSheet by remember { mutableStateOf(false) }
    var targetCreateDate by remember { mutableStateOf(LocalDate.now()) }
    var viewingTask by remember { mutableStateOf<TaskItem?>(null) }
    var viewingProject by remember { mutableStateOf<TaskChinhItem?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isTabLoading by remember { mutableStateOf(false) }

    // Phản hồi tức thì cho mục tiêu từ Widget
    var pendingWidgetTaskId by remember { mutableStateOf<Long?>(null) }
    var pendingWidgetProjectId by remember { mutableStateOf<Long?>(null) }

    val isSyncing by repository.isSyncing.collectAsState()
    val tasks by repository.tasks.collectAsState()
    val taskChinhList by repository.taskChinhList.collectAsState()
    val users by repository.users.collectAsState()
    val userEmail = repository.getSavedEmail().lowercase().trim()
    val curUser = users.find { it.email.equals(userEmail, ignoreCase = true) }
    LaunchedEffect(curUser) {
        if (curUser != null && curUser.name.isNotBlank()) {
            repository.saveUserName(curUser.name)
        }
    }
    val currentUserName = curUser?.name ?: repository.getSavedUserName()

    // Tu dong dong bo SharePoint khi mo ung dung
    LaunchedEffect(Unit) {
        repository.fetchAllFromSharePoint()
    }

    // Phản hồi chuyển trang tức thì trên Navbar kèm vòng xoay tải dữ liệu mượt mà
    LaunchedEffect(selectedTab) {
        kotlinx.coroutines.delay(60)
        isTabLoading = false
    }

    // Xử lý sự kiện mở từ Widget tức thì, lập tức chuyển trang không bị đơ
    val currentWidgetIntent = intentState.value
    LaunchedEffect(currentWidgetIntent) {
        val targetIntent = currentWidgetIntent ?: return@LaunchedEffect
        val openTaskId = targetIntent.getLongExtra("open_task_id", -1L)
        val openProjectId = targetIntent.getLongExtra("open_project_id", -1L)
        val openTab = targetIntent.getIntExtra("open_tab", -1)
        val openCreate = targetIntent.getBooleanExtra("open_create_task", false)

        // Chuyển trang đích NGAY LẬP TỨC
        if (openTab in 0..3) {
            selectedTab = openTab
        } else if (openProjectId > 0L) {
            selectedTab = 1
            pendingWidgetProjectId = openProjectId
        } else if (openTaskId > 0L) {
            selectedTab = 0
            pendingWidgetTaskId = openTaskId
        }

        if (openCreate) {
            val dateStr = targetIntent.getStringExtra("target_create_date")
            targetCreateDate = parseLocalDate(dateStr) ?: LocalDate.now()
            showCreateBottomSheet = true
        }

        intentState.value = null
    }

    // Tự động mở Modal chi tiết khi dữ liệu từ Widget tải xong
    LaunchedEffect(pendingWidgetTaskId, tasks) {
        val targetId = pendingWidgetTaskId ?: return@LaunchedEffect
        val found = tasks.find { it.id == targetId }
        if (found != null) {
            viewingTask = found
            pendingWidgetTaskId = null
        }
    }

    LaunchedEffect(pendingWidgetProjectId, taskChinhList) {
        val targetId = pendingWidgetProjectId ?: return@LaunchedEffect
        val found = taskChinhList.find { it.id == targetId }
        if (found != null) {
            viewingProject = found
            pendingWidgetProjectId = null
        }
    }

    BackHandler(enabled = viewingProject != null || viewingTask != null || showCreateBottomSheet) {
        if (viewingProject != null) {
            viewingProject = null
        } else if (viewingTask != null) {
            viewingTask = null
        } else if (showCreateBottomSheet) {
            showCreateBottomSheet = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            FloatingAppNavBar(
                selectedTab = selectedTab,
                onTabSelect = { newTab ->
                    if (newTab != selectedTab) {
                        isTabLoading = true
                        selectedTab = newTab
                    }
                },
                onAddClick = {
                    targetCreateDate = LocalDate.now()
                    showCreateBottomSheet = true
                }
            )
        }
    ) { innerPadding ->
        if (selectedTab in 0..2) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    coroutineScope.launch {
                        isRefreshing = true
                        repository.fetchAllFromSharePoint()
                        isRefreshing = false
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTabLoading || (isSyncing && tasks.isEmpty() && taskChinhList.isEmpty())) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                color = PrimaryPurple,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = if (appLang == "en") "Loading data..." else "Đang tải dữ liệu...",
                                fontSize = 12.5.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    when (selectedTab) {
                        0 -> MyTasksTab(
                            repository = repository,
                            tasks = tasks,
                            taskChinhList = taskChinhList,
                            userEmail = userEmail,
                            onTaskClick = { viewingTask = it },
                            onProjectClick = { viewingProject = it }
                        )
                        1 -> DepartmentTab(
                            repository = repository,
                            tasks = tasks,
                            taskChinhList = taskChinhList,
                            userEmail = userEmail,
                            onProjectClick = { viewingProject = it },
                            onTaskClick = { viewingTask = it }
                        )
                        2 -> CalendarTab(
                            repository = repository,
                            tasks = tasks,
                            taskChinhList = taskChinhList,
                            userEmail = userEmail,
                            onAddClick = { date ->
                                targetCreateDate = date
                                showCreateBottomSheet = true
                            },
                            onTaskClick = { viewingTask = it },
                            onProjectClick = { viewingProject = it }
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTabLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = PrimaryPurple,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                } else {
                    SettingsTab(
                        repository = repository,
                        userEmail = userEmail,
                        appLang = appLang,
                        onSelectLang = onSelectLang,
                        onLogout = onLogout
                    )
                }
            }
        }
    }

    // Modal chi tiết Task
    viewingTask?.let { task ->
        val liveTask = tasks.find { it.id == task.id } ?: task
        val parentProj = taskChinhList.find { it.id == liveTask.taskChinhId }
        val canEdit = isTaskRelatedToUser(liveTask, parentProj, userEmail, currentUserName)
        TaskDetailBottomSheet(
            task = liveTask,
            parentProject = parentProj,
            canEdit = canEdit,
            onDismiss = { viewingTask = null },
            onToggle = {
                if (canEdit) {
                    coroutineScope.launch {
                        val res = repository.toggleTaskCompletion(liveTask.id)
                        if (res.isSuccess) {
                            viewingTask = liveTask.copy(isCompleted = !liveTask.isCompleted)
                        }
                    }
                }
            },
            onOpenParentProject = { parentId ->
                val targetProj = taskChinhList.find { it.id == parentId } ?: parentProj
                if (targetProj != null) {
                    viewingTask = null
                    viewingProject = targetProj
                }
            }
        )
    }

    // Modal chi tiết Dự án / TaskChinh
    viewingProject?.let { proj ->
        val liveProj = taskChinhList.find { it.id == proj.id } ?: proj
        val childTasks = tasks.filter {
            it.taskChinhId == liveProj.id || (it.taskChinhId == null && it.safeTaskChinhTitle.isNotBlank() && it.safeTaskChinhTitle.equals(liveProj.safeTitle, ignoreCase = true))
        }
        ProjectDetailBottomSheet(
            project = liveProj,
            childTasks = childTasks,
            currentUserEmail = userEmail,
            users = users,
            onDismiss = { viewingProject = null },
            onToggleChild = { childId ->
                coroutineScope.launch {
                    repository.toggleTaskCompletion(childId)
                }
            },
            onTaskClick = { clickedTask ->
                viewingProject = null
                viewingTask = clickedTask
            },
            onRequestExtension = { newDate, reason ->
                coroutineScope.launch {
                    val res = repository.requestTaskExtension(liveProj.id, newDate, reason)
                    if (res.isSuccess) {
                        Toast.makeText(context, if (appLang == "en") "Extension request submitted" else "Đã gửi yêu cầu dời hạn thành công", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, if (appLang == "en") "Extension error: ${res.exceptionOrNull()?.message}" else "Lỗi dời hạn: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onAddChildTask = { title, sDate, eDate, name, email ->
                coroutineScope.launch {
                    val res = repository.createTaskChiTiet(
                        title = title,
                        taskChinhId = liveProj.id,
                        taskChinhTitle = liveProj.safeTitle,
                        startDate = sDate,
                        endDate = eDate,
                        assigneeName = name,
                        assigneeEmail = email
                    )
                    if (res.isSuccess) {
                        Toast.makeText(context, if (appLang == "en") "Subtask added to: ${liveProj.safeTitle}" else "Đã thêm việc con vào: ${liveProj.safeTitle}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, if (appLang == "en") "Error: ${res.exceptionOrNull()?.message}" else "Lỗi: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    if (showCreateBottomSheet) {
        CreateTaskBottomSheet(
            selectedDate = targetCreateDate,
            taskChinhList = taskChinhList,
            currentUserEmail = userEmail,
            users = users,
            onDismiss = { showCreateBottomSheet = false },
            onCreateChiTiet = { title, parentId, parentTitle, sDate, eDate, name, email ->
                showCreateBottomSheet = false
                coroutineScope.launch {
                    val res = repository.createTaskChiTiet(
                        title = title,
                        taskChinhId = parentId,
                        taskChinhTitle = parentTitle,
                        startDate = sDate,
                        endDate = eDate,
                        assigneeName = name,
                        assigneeEmail = email
                    )
                    if (res.isSuccess) {
                        Toast.makeText(context, if (appLang == "en") "Task added successfully" else "Đã thêm công việc thành công", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, if (appLang == "en") "Error: ${res.exceptionOrNull()?.message}" else "Lỗi: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onCreateChinh = { title, sDate, eDate, person, email, dept, pTask, pViec, ccName, ccEmail ->
                showCreateBottomSheet = false
                val curUser = users.find { it.email.equals(userEmail, ignoreCase = true) }
                val creatorName = curUser?.name ?: "Sang Nguyễn"
                val creatorEmail = userEmail.ifBlank { "sang.nguyen@seazen.com.vn" }
                coroutineScope.launch {
                    val res = repository.createTaskChinh(
                        title = title,
                        startDate = sDate,
                        endDate = eDate,
                        responsiblePerson = person,
                        responsibleEmail = email,
                        phongBan = dept,
                        phanLoaiTask = pTask,
                        phanLoaiViec = pViec,
                        creatorName = creatorName,
                        creatorEmail = creatorEmail,
                        ccName = ccName,
                        ccEmail = ccEmail
                    )
                    if (res.isSuccess) {
                        Toast.makeText(context, if (appLang == "en") "New project created successfully" else "Đã tạo công việc chính mới", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, if (appLang == "en") "Error: ${res.exceptionOrNull()?.message}" else "Lỗi: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
}

val APP_DEPARTMENTS = listOf(
    "SeaZen Tech",
    "Điều Hành",
    "Tuyển Dụng",
    "Đào Tạo",
    "Marketing",
    "Hành Chính",
    "C&B",
    "Kế Toán",
    "Pháp Lý",
    "Kinh Doanh",
    "Vận Hành"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScopeFilterBar(
    selectedScope: String,
    onSelectScope: (String) -> Unit,
    modifier: Modifier = Modifier,
    userDept: String = "SeaZen Tech"
) {
    val lang = LocalAppLanguage.current
    val str = AppStrings.of(lang)
    var showModalSheet by remember { mutableStateOf(false) }

    val scopeChips = remember(userDept, str) {
        listOf(
            "PERSONAL" to str.filterScopePersonal,
            "ALL" to str.filterScopeAll
        ) + (if (userDept.isNotBlank() && userDept != "ALL" && !APP_DEPARTMENTS.contains(userDept)) listOf(userDept to userDept) else emptyList()) +
        APP_DEPARTMENTS.map { it to it }
    }

    val currentDisplayLabel = when (selectedScope) {
        "PERSONAL" -> str.labelScopePersonal
        "ALL" -> str.labelScopeAll
        else -> selectedScope
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Nút mở modal bộ lọc có giao diện riêng
        Surface(
            onClick = { showModalSheet = true },
            shape = RoundedCornerShape(10.dp),
            color = PillBg,
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE2E8F0))),
            modifier = Modifier.height(34.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = str.filterScopeTitle,
                    tint = PrimaryPurple,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = currentDisplayLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryPurple,
                    maxLines = 1
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Dải filter dạng lướt ngang (Horizontal Scrollable Chips)
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(scopeChips, key = { it.first }) { (scopeKey, scopeTitle) ->
                val isSelected = (selectedScope == scopeKey)
                Surface(
                    onClick = { onSelectScope(scopeKey) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (isSelected) PrimaryPurple else PillBg,
                    border = if (isSelected) null else CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE2E8F0))),
                    modifier = Modifier.height(32.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (scopeKey == "PERSONAL") {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else TextMuted,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = scopeTitle,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else TextDark
                        )
                    }
                }
            }
        }
    }

    if (showModalSheet) {
        ScopeFilterBottomSheet(
            selectedScope = selectedScope,
            onSelectScope = {
                onSelectScope(it)
                showModalSheet = false
            },
            onDismiss = { showModalSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScopeFilterBottomSheet(
    selectedScope: String,
    onSelectScope: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val lang = LocalAppLanguage.current
    val str = AppStrings.of(lang)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchDeptQuery by remember { mutableStateOf("") }

    val filteredDepts = remember(searchDeptQuery) {
        if (searchDeptQuery.isBlank()) APP_DEPARTMENTS
        else APP_DEPARTMENTS.filter { it.contains(searchDeptQuery, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = str.filterScopeTitle,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Text(
                        text = str.filterScopeSubtitle,
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = str.actionClose, tint = TextMuted)
                }
            }

            // Ô tìm kiếm phòng ban
            OutlinedTextField(
                value = searchDeptQuery,
                onValueChange = { searchDeptQuery = it },
                placeholder = { Text(str.searchDepartmentPlaceholder, fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = PrimaryPurple, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchDeptQuery.isNotBlank()) {
                        IconButton(onClick = { searchDeptQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            )

            // Danh sách lựa chọn có thể cuộn (LazyColumn)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Mục 1: Cá nhân (Mặc định)
                item {
                    val isSelected = selectedScope == "PERSONAL"
                    Surface(
                        onClick = { onSelectScope("PERSONAL") },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) AccentPurpleLight else Color.Transparent,
                        border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(PrimaryPurple.copy(alpha = 0.5f))) else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(shape = CircleShape, color = if (isSelected) PrimaryPurple else PillBg, modifier = Modifier.size(32.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = if (isSelected) Color.White else PrimaryPurple, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Column {
                                    Text(text = str.filterScopePersonal, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isSelected) PrimaryPurple else TextDark)
                                    Text(text = if (lang == "en") "Assigned to you directly" else "Công việc được giao cho bạn", fontSize = 11.sp, color = TextMuted)
                                }
                            }
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = PrimaryPurple, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Mục 2: Toàn công ty / Tất cả
                item {
                    val isSelected = selectedScope == "ALL"
                    Surface(
                        onClick = { onSelectScope("ALL") },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) AccentPurpleLight else Color.Transparent,
                        border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(PrimaryPurple.copy(alpha = 0.5f))) else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(shape = CircleShape, color = if (isSelected) PrimaryPurple else PillBg, modifier = Modifier.size(32.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Public, contentDescription = null, tint = if (isSelected) Color.White else PrimaryPurple, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Column {
                                    Text(text = str.filterScopeAll, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isSelected) PrimaryPurple else TextDark)
                                    Text(text = if (lang == "en") "Display all departments & tasks" else "Xem toàn bộ mục tiêu đơn vị", fontSize = 11.sp, color = TextMuted)
                                }
                            }
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = PrimaryPurple, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Phân mục các phòng ban
                items(filteredDepts, key = { it }) { dept ->
                    val isSelected = selectedScope == dept
                    Surface(
                        onClick = { onSelectScope(dept) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) AccentPurpleLight else Color.Transparent,
                        border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(PrimaryPurple.copy(alpha = 0.5f))) else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(shape = CircleShape, color = if (isSelected) PrimaryPurple else PillBg, modifier = Modifier.size(32.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.CorporateFare, contentDescription = null, tint = if (isSelected) Color.White else TextMuted, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Text(text = dept, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) PrimaryPurple else TextDark)
                            }
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = PrimaryPurple, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * TAB 1: CÔNG VIỆC CÁ NHÂN (MY TASKS)
 * Phong cách ảnh tham chiếu 1: Avatar, Greeting, Thẻ Carousel trượt ngang, danh sách việc cá nhân
 */
@Composable
fun MyTasksTab(
    repository: TaskRepository,
    tasks: List<TaskItem>,
    taskChinhList: List<TaskChinhItem>,
    userEmail: String,
    onTaskClick: (TaskItem) -> Unit,
    onProjectClick: (TaskChinhItem) -> Unit
) {
    val str = AppStrings.of(LocalAppLanguage.current)
    val coroutineScope = rememberCoroutineScope()
    var selectedScope by remember { mutableStateOf("PERSONAL") } // MẶC ĐỊNH LUÔN LÀ CÔNG VIỆC CÁ NHÂN
    var filterType by remember { mutableStateOf("today") } // "today", "upcoming", "overdue", "all"
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val projectMap = remember(taskChinhList) { taskChinhList.associateBy { it.id } }
    val scopedTasks = remember(tasks, projectMap, userEmail, selectedScope) {
        when (selectedScope) {
            "PERSONAL" -> {
                val savedName = repository.getSavedUserName()
                tasks.filter { com.seazen.task.widget.SeaZenWidgetProvider.isMyTask(it, userEmail, savedName) }
            }
            "ALL" -> tasks
            else -> {
                tasks.filter { t ->
                    val parent = projectMap[t.taskChinhId]
                    parent?.safePhongBan.equals(selectedScope, ignoreCase = true)
                }
            }
        }
    }

    val today = LocalDate.now()
    val overdueCount = remember(scopedTasks, today) { scopedTasks.count { it.isOverdue(today) } }

    val displayTasks = remember(scopedTasks, filterType, searchQuery, today) {
        val base = when (filterType) {
            "today" -> scopedTasks.filter { it.isActiveOn(today) }
            "upcoming" -> scopedTasks.filter { !it.isCompleted }
            "overdue" -> scopedTasks.filter { it.isOverdue(today) }
            else -> scopedTasks
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            base.filter {
                it.safeTitle.lowercase().contains(q) ||
                it.safeTaskChinhTitle.lowercase().contains(q) ||
                it.safeAssigneeName.lowercase().contains(q)
            }
        } else base
    }

    val scopedProjects = remember(taskChinhList, userEmail, selectedScope) {
        when (selectedScope) {
            "PERSONAL" -> {
                val list = taskChinhList.filter { it.safeResponsibleEmail.lowercase().contains(userEmail) || it.safeResponsiblePerson.lowercase().contains("sang") }
                if (list.isNotEmpty()) list else taskChinhList.take(3)
            }
            "ALL" -> taskChinhList
            else -> taskChinhList.filter { it.safePhongBan.equals(selectedScope, ignoreCase = true) }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Top Greeting or Search Bar
        item {
            if (isSearchOpen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(str.searchPlaceholder, fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = PrimaryPurple, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = str.actionDelete, tint = TextMuted, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        searchQuery = ""
                        isSearchOpen = false
                    }) {
                        Text(str.actionClose, fontSize = 13.sp, color = PrimaryPurple, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = PrimaryPurple,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color.White)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "${str.greetingPrefix} Sang Nguyễn",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Text(
                                text = str.greetingSub,
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }

                    IconButton(onClick = { isSearchOpen = true }) {
                        Icon(Icons.Default.Search, contentDescription = str.searchPlaceholder, tint = TextDark)
                    }
                }
            }
        }

        // Thanh Bộ lọc Phạm vi & Phòng ban (Mặc định là bản thân / cá nhân)
        item {
            ScopeFilterBar(
                selectedScope = selectedScope,
                onSelectScope = { selectedScope = it }
            )
        }

        // Khối Thống Kê Tổng Quan Hôm Nay (Daily Overview)
        item {
            val todayTasks = remember(scopedTasks, today) { scopedTasks.filter { it.isActiveOn(today) } }
            val doneToday = remember(todayTasks) { todayTasks.count { it.isCompleted } }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorderColor))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(text = "${todayTasks.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryPurple)
                        Text(text = str.statToday, fontSize = 11.sp, color = TextMuted)
                    }

                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(CardBorderColor))

                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(text = "$doneToday/${todayTasks.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                        Text(text = str.statDone, fontSize = 11.sp, color = TextMuted)
                    }

                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(CardBorderColor))

                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$overdueCount",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (overdueCount > 0) Color(0xFFDC2626) else TextMuted
                        )
                        Text(text = str.statOverdue, fontSize = 11.sp, color = TextMuted)
                    }
                }
            }
        }

        // Section: Thẻ dự án trượt ngang (Horizontal Carousel - Ảnh 2)
        item {
            Column {
                Text(
                    text = str.sectionMyProjects,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(scopedProjects) { proj ->
                        val pTasks = tasks.filter { it.taskChinhId == proj.id || (it.taskChinhId == null && it.safeTaskChinhTitle.isNotBlank() && it.safeTaskChinhTitle.equals(proj.safeTitle, ignoreCase = true)) }
                        val displayProgress = proj.computedProgress(pTasks)
                        Card(
                            modifier = Modifier
                                .width(220.dp)
                                .height(140.dp)
                                .clickable { onProjectClick(proj) },
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = PrimaryPurple)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            listOf(PrimaryPurple, PrimaryPurpleDark)
                                        )
                                    )
                                    .padding(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color.White.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = proj.safePhongBan,
                                                fontSize = 10.sp,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = proj.safeTitle,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(text = str.labelProgress, fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
                                            Text(text = "$displayProgress%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        LinearProgressIndicator(
                                            progress = { (displayProgress / 100f).coerceIn(0f, 1f) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                            color = Color.White,
                                            trackColor = Color.White.copy(alpha = 0.3f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Filter Pills: Hôm nay | Cần làm | Trễ hạn | Tất cả
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterTabPill(str.filterToday, filterType == "today") { filterType = "today" }
                FilterTabPill(str.filterUpcoming, filterType == "upcoming") { filterType = "upcoming" }
                FilterTabPill(
                    text = if (overdueCount > 0) "${str.filterOverdue} ($overdueCount)" else str.filterOverdue,
                    isSelected = filterType == "overdue"
                ) { filterType = "overdue" }
                FilterTabPill(str.filterAll, filterType == "all") { filterType = "all" }
            }
        }

        // Danh sách công việc cá nhân
        if (displayTasks.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorderColor))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = str.emptyCategoryTasks, fontSize = 13.sp, color = TextMuted)
                    }
                }
            }
        } else {
            items(displayTasks, key = { it.id }) { task ->
                val parentProj = taskChinhList.find { it.id == task.taskChinhId }
                val canEdit = isTaskRelatedToUser(task, parentProj, userEmail, repository.getSavedUserName())
                PersonalTaskCard(
                    task = task,
                    canEdit = canEdit,
                    onToggle = {
                        if (canEdit) {
                            coroutineScope.launch {
                                repository.toggleTaskCompletion(task.id)
                            }
                        }
                    },
                    onClick = { onTaskClick(task) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

/**
 * TAB 2: CÔNG VIỆC PHÒNG BAN & CÔNG TY
 * Hỗ trợ bộ lọc Phòng ban, Phân loại, Trạng thái và cây phân cấp TaskChinh - TaskChiTiet
 */
@Composable
fun DepartmentTab(
    repository: TaskRepository,
    tasks: List<TaskItem>,
    taskChinhList: List<TaskChinhItem>,
    userEmail: String = "",
    onProjectClick: (TaskChinhItem) -> Unit,
    onTaskClick: (TaskItem) -> Unit
) {
    val str = AppStrings.of(LocalAppLanguage.current)
    val coroutineScope = rememberCoroutineScope()
    val resolvedEmail = remember(userEmail) {
        userEmail.ifBlank { repository.getSavedEmail() }.lowercase().trim()
    }
    var selectedScope by remember { mutableStateOf("PERSONAL") } // MẶC ĐỊNH LUÔN LÀ CÔNG VIỆC CÁ NHÂN
    var selectedStatus by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }

    val tasksByProjectId = remember(tasks) { tasks.groupBy { it.taskChinhId } }
    val unassignedTasksByTitle = remember(tasks) {
        tasks.filter { it.taskChinhId == null && it.safeTaskChinhTitle.isNotBlank() }
            .groupBy { it.safeTaskChinhTitle.lowercase().trim() }
    }

    val filteredProjects = remember(taskChinhList, tasksByProjectId, unassignedTasksByTitle, selectedScope, selectedStatus, searchQuery, resolvedEmail) {
        taskChinhList.filter { p ->
            val matchScope = when (selectedScope) {
                "PERSONAL" -> {
                    p.safeResponsibleEmail.lowercase().contains(resolvedEmail) ||
                    p.safeCreatorEmail.lowercase().contains(resolvedEmail) ||
                    p.safeResponsiblePerson.lowercase().contains("sang")
                }
                "ALL" -> true
                else -> p.safePhongBan.equals(selectedScope, ignoreCase = true)
            }
            if (!matchScope) return@filter false

            val byId = tasksByProjectId[p.id] ?: emptyList()
            val byTitle = unassignedTasksByTitle[p.safeTitle.lowercase().trim()] ?: emptyList()
            val pTasks = if (byTitle.isEmpty()) byId else byId + byTitle
            val pProg = p.computedProgress(pTasks)
            val isProjOverdue = p.isOverdue() && pProg < 100
            val matchStatus = when (selectedStatus) {
                "DONE" -> pProg >= 100 || p.safeTrangThai.contains("Hoàn thành", ignoreCase = true)
                "PROGRESS" -> pProg < 100 && !isProjOverdue
                "OVERDUE" -> isProjOverdue
                else -> true
            }
            if (!matchStatus) return@filter false

            val matchSearch = searchQuery.isBlank() ||
                    p.safeTitle.contains(searchQuery, ignoreCase = true) ||
                    p.safeResponsiblePerson.contains(searchQuery, ignoreCase = true) ||
                    p.safePhongBan.contains(searchQuery, ignoreCase = true)
            matchSearch
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            Column {
                Text(
                    text = str.deptTabTitle,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Text(
                    text = str.deptTabSubtitle,
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }

        // Ô tìm kiếm dự án
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(str.searchDeptPlaceholder, fontSize = 13.sp, color = TextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = PrimaryPurple,
                    unfocusedBorderColor = CardBorderColor
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Bộ lọc phạm vi & phòng ban chuẩn hóa (Lướt ngang & Giao diện chuyên dụng)
        item {
            ScopeFilterBar(
                selectedScope = selectedScope,
                onSelectScope = { selectedScope = it }
            )
        }

        // Bộ lọc trạng thái
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterTabPill(str.filterAll, selectedStatus == "ALL") { selectedStatus = "ALL" }
                FilterTabPill(str.tagInProgress, selectedStatus == "PROGRESS") { selectedStatus = "PROGRESS" }
                FilterTabPill(str.tagOverdue, selectedStatus == "OVERDUE") { selectedStatus = "OVERDUE" }
                FilterTabPill(str.statDone, selectedStatus == "DONE") { selectedStatus = "DONE" }
            }
        }

        // Danh sách TaskChinh của phòng ban
        if (filteredProjects.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorderColor))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = str.emptyFilterProjects, fontSize = 13.sp, color = TextMuted)
                    }
                }
            }
        } else {
            items(filteredProjects, key = { it.id }) { proj ->
                DepartmentProjectCard(
                    project = proj,
                    childTasks = tasks.filter { it.taskChinhId == proj.id || (it.taskChinhId == null && it.safeTaskChinhTitle.isNotBlank() && it.safeTaskChinhTitle.equals(proj.safeTitle, ignoreCase = true)) },
                    currentUserEmail = resolvedEmail,
                    currentUserName = repository.getSavedUserName(),
                    onToggleChild = { taskId ->
                        coroutineScope.launch {
                            repository.toggleTaskCompletion(taskId)
                        }
                    },
                    onClick = { onProjectClick(proj) },
                    onTaskClick = onTaskClick,
                    onAddChildClick = { onProjectClick(proj) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

/**
 * Thẻ công việc Phòng ban (TaskChinh) kèm danh sách Task con trực thuộc
 */
@Composable
fun DepartmentProjectCard(
    project: TaskChinhItem,
    childTasks: List<TaskItem>,
    currentUserEmail: String = "",
    currentUserName: String = "",
    onToggleChild: (Long) -> Unit,
    onClick: () -> Unit = {},
    onTaskClick: (TaskItem) -> Unit = {},
    onAddChildClick: () -> Unit = {}
) {
    val lang = LocalAppLanguage.current
    val str = AppStrings.of(lang)
    val displayProgress = project.computedProgress(childTasks)
    val isOverdue = project.isOverdue() && displayProgress < 100

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorderColor)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AccentPurpleLight
                    ) {
                        Text(
                            text = project.safePhongBan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryPurple,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = PillBg
                    ) {
                        Text(
                            text = project.safePhanLoaiTask,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextDark,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    if (project.safePhanLoaiViec.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = PillBg
                        ) {
                            Text(
                                text = project.safePhanLoaiViec,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Normal,
                                color = TextMuted,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (isOverdue) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFEE2E2)
                        ) {
                            Text(
                                text = str.tagOverdue,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "$displayProgress%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryPurple
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = project.safeTitle,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${str.labelLead} ${project.safeResponsiblePerson}",
                    fontSize = 11.sp,
                    color = TextMuted
                )
                Text(
                    text = CalendarUtils.formatDisplayRange(project.safeStartDate, project.safeEndDate, lang),
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }

            if (project.safeCreatorName.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${str.labelAssignedBy}: ${project.safeCreatorName}",
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { (displayProgress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = PrimaryPurple,
                trackColor = PillBg
            )

            // Danh sách việc con trực thuộc
            if (childTasks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = PillBg)
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${str.sectionProjectSubtasks} (${childTasks.count { it.isCompleted }}/${childTasks.size} ${str.labelDoneSuffix}):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMuted
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    childTasks.forEach { ct ->
                        val isChildUrgent = ct.safePriority.lowercase() in listOf("urgent", "khẩn cấp", "high")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onTaskClick(ct) }
                                .background(PillBg.copy(alpha = 0.5f))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val canEditChild = isTaskRelatedToUser(ct, project, currentUserEmail, currentUserName)
                            Checkbox(
                                checked = ct.isCompleted,
                                onCheckedChange = { if (canEditChild) onToggleChild(ct.id) },
                                enabled = canEditChild,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = PrimaryPurple,
                                    disabledCheckedColor = Color(0xFF94A3B8),
                                    disabledUncheckedColor = Color(0xFFCBD5E1)
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = ct.safeTitle,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (ct.isCompleted) TextMuted else TextDark,
                                        textDecoration = if (ct.isCompleted) TextDecoration.LineThrough else null,
                                        modifier = Modifier.weight(1f, fill = false),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isChildUrgent) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFFEE2E2)
                                        ) {
                                            Text(
                                                text = str.tagUrgent,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFDC2626),
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                if (ct.safeAssigneeName.isNotBlank() || ct.safeEndDate.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${ct.safeAssigneeName} • ${CalendarUtils.formatDisplayRange(ct.safeStartDate, ct.safeEndDate, lang)}",
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${childTasks.size} ${str.labelSubtasks}",
                    fontSize = 11.sp,
                    color = TextMuted
                )
                Surface(
                    onClick = onAddChildClick,
                    shape = RoundedCornerShape(6.dp),
                    color = AccentPurpleLight
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = PrimaryPurple, modifier = Modifier.size(12.dp))
                        Text(text = str.actionAddSubtask, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryPurple)
                    }
                }
            }
        }
    }
}

/**
 * TAB 3: LỊCH & DÒNG THỜI GIAN
 */
@Composable
fun CalendarTab(
    repository: TaskRepository,
    tasks: List<TaskItem>,
    taskChinhList: List<TaskChinhItem>,
    userEmail: String = "",
    onAddClick: (LocalDate) -> Unit,
    onTaskClick: (TaskItem) -> Unit,
    onProjectClick: (TaskChinhItem) -> Unit = {}
) {
    val lang = LocalAppLanguage.current
    val str = AppStrings.of(lang)
    val isVi = lang != "en"
    val coroutineScope = rememberCoroutineScope()
    val resolvedEmail = remember(userEmail) {
        userEmail.ifBlank { repository.getSavedEmail() }.lowercase().trim()
    }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var currentYearMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedScope by remember { mutableStateOf("PERSONAL") } // MẶC ĐỊNH LUÔN LÀ CÔNG VIỆC CÁ NHÂN
    var searchQuery by remember { mutableStateOf("") }
    var isSearchOpen by remember { mutableStateOf(false) }

    val projectMap = remember(taskChinhList) { taskChinhList.associateBy { it.id } }

    val dayProjects = remember(taskChinhList, selectedDate, selectedScope, searchQuery, resolvedEmail) {
        taskChinhList.filter { p ->
            val active = p.isActiveOn(selectedDate)
            if (!active) return@filter false
            val matchScope = when (selectedScope) {
                "PERSONAL" -> {
                    p.safeResponsibleEmail.lowercase().contains(resolvedEmail) ||
                    p.safeCreatorEmail.lowercase().contains(resolvedEmail) ||
                    p.safeResponsiblePerson.lowercase().contains("sang")
                }
                "ALL" -> true
                else -> p.safePhongBan.equals(selectedScope, ignoreCase = true)
            }
            if (!matchScope) return@filter false
            val matchSearch = searchQuery.isBlank() ||
                    p.safeTitle.contains(searchQuery, ignoreCase = true) ||
                    p.safeResponsiblePerson.contains(searchQuery, ignoreCase = true) ||
                    p.safePhongBan.contains(searchQuery, ignoreCase = true)
            matchSearch
        }
    }

    val dayTasks = remember(tasks, projectMap, selectedDate, selectedScope, searchQuery, resolvedEmail) {
        tasks.filter { t ->
            val active = t.isActiveOn(selectedDate)
            if (!active) return@filter false
            val matchScope = when (selectedScope) {
                "PERSONAL" -> {
                    t.safeAssigneeEmail.lowercase().contains(resolvedEmail) ||
                    t.safeAssigneeName.lowercase().contains("sang")
                }
                "ALL" -> true
                else -> {
                    val parent = projectMap[t.taskChinhId]
                    parent?.safePhongBan.equals(selectedScope, ignoreCase = true)
                }
            }
            if (!matchScope) return@filter false
            val matchSearch = searchQuery.isBlank() ||
                    t.safeTitle.contains(searchQuery, ignoreCase = true) ||
                    t.safeTaskChinhTitle.contains(searchQuery, ignoreCase = true) ||
                    t.safeAssigneeName.contains(searchQuery, ignoreCase = true)
            matchSearch
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Header chuyển tháng & nút tìm kiếm
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = CalendarUtils.formatHeaderDate(selectedDate, lang),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Text(
                        text = CalendarUtils.formatMonthYear(currentYearMonth, lang),
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isSearchOpen = !isSearchOpen }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = str.searchCalendarPlaceholder,
                            tint = if (isSearchOpen || searchQuery.isNotBlank()) PrimaryPurple else TextDark
                        )
                    }
                    IconButton(onClick = { currentYearMonth = currentYearMonth.minusMonths(1) }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = TextDark)
                    }
                    IconButton(onClick = {
                        currentYearMonth = YearMonth.now()
                        selectedDate = LocalDate.now()
                    }) {
                        Icon(Icons.Default.Today, contentDescription = null, tint = PrimaryPurple)
                    }
                    IconButton(onClick = { currentYearMonth = currentYearMonth.plusMonths(1) }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextDark)
                    }
                }
            }
        }

        // Ô tìm kiếm cho trang lịch (nếu được mở)
        if (isSearchOpen) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(str.searchCalendarPlaceholder, fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = PrimaryPurple, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = str.actionDelete, tint = TextMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    TextButton(onClick = {
                        searchQuery = ""
                        isSearchOpen = false
                    }) {
                        Text(str.actionClose, fontSize = 12.sp, color = PrimaryPurple, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Lưới lịch tháng
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorderColor))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    val weekDays = if (isVi) listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN") else listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
                    Row(modifier = Modifier.fillMaxWidth()) {
                        weekDays.forEach { w ->
                            Text(
                                text = w,
                                fontSize = 12.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val monthGrid = remember(currentYearMonth) { CalendarUtils.getDaysInMonthGrid(currentYearMonth) }
                    val monthSummaryMap = remember(monthGrid, tasks, projectMap, selectedScope, resolvedEmail) {
                        val validDays = monthGrid.filterNotNull()
                        if (validDays.isEmpty()) emptyMap<LocalDate, Triple<Int, Int, Boolean>>()
                        else {
                            val today = LocalDate.now()
                            val scopedT = tasks.filter { t ->
                                when (selectedScope) {
                                    "PERSONAL" -> t.safeAssigneeEmail.lowercase().contains(resolvedEmail) || t.safeAssigneeName.lowercase().contains("sang")
                                    "ALL" -> true
                                    else -> {
                                        val p = projectMap[t.taskChinhId]
                                        p?.safePhongBan.equals(selectedScope, ignoreCase = true)
                                    }
                                }
                            }
                            val scopedP = taskChinhList.filter { p ->
                                when (selectedScope) {
                                    "PERSONAL" -> p.safeResponsibleEmail.lowercase().contains(resolvedEmail) || p.safeCreatorEmail.lowercase().contains(resolvedEmail) || p.safeResponsiblePerson.lowercase().contains("sang")
                                    "ALL" -> true
                                    else -> p.safePhongBan.equals(selectedScope, ignoreCase = true)
                                }
                            }
                            validDays.associateWith { dayDate ->
                                val tCount = scopedT.count { it.isActiveOn(dayDate) }
                                val pCount = scopedP.count { it.isActiveOn(dayDate) }
                                val hasOverdue = (tCount > 0 && scopedT.any { it.isActiveOn(dayDate) && it.isOverdue(today) }) ||
                                                 (pCount > 0 && scopedP.any { it.isActiveOn(dayDate) && it.isOverdue(today) })
                                Triple(tCount, pCount, hasOverdue)
                            }
                        }
                    }

                    val rows = monthGrid.chunked(7)
                    rows.forEach { weekRow ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            weekRow.forEach { dayDate ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (dayDate != null) {
                                        val isSelected = (dayDate == selectedDate)
                                        val isToday = (dayDate == LocalDate.now())
                                        val summary = monthSummaryMap[dayDate]
                                        val taskCount = summary?.first ?: 0
                                        val projectCount = summary?.second ?: 0
                                        val hasOverdue = summary?.third ?: false

                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                                .background(if (isSelected) PrimaryDark else Color.Transparent)
                                                .clickable { selectedDate = dayDate },
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "${dayDate.dayOfMonth}",
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                color = when {
                                                    isSelected -> Color.White
                                                    isToday -> PrimaryPurple
                                                    else -> TextDark
                                                }
                                            )
                                            if ((taskCount > 0 || projectCount > 0) && !isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .background(if (hasOverdue) Color(0xFFDC2626) else PrimaryPurple, CircleShape)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            for (i in weekRow.size until 7) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // Thanh Bộ lọc Phạm vi & Phòng ban cho trang Lịch (Lướt ngang & Giao diện chuyên dụng)
        item {
            ScopeFilterBar(
                selectedScope = selectedScope,
                onSelectScope = { selectedScope = it }
            )
        }

        // Tiêu đề ngày được chọn kèm nút thêm nhanh
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${str.labelScheduleForDay} ${selectedDate.dayOfMonth}/${selectedDate.monthValue}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )

                Surface(
                    onClick = { onAddClick(selectedDate) },
                    shape = CircleShape,
                    color = PrimaryPurple,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // 1. Phân mục: Dự án / Công việc chính đang diễn ra trong ngày
        if (dayProjects.isNotEmpty()) {
            item {
                Text(
                    text = "${str.sectionDayProjects} (${dayProjects.size}):",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
            }

            items(dayProjects, key = { "proj_${it.id}" }) { proj ->
                val pTasks = tasks.filter { it.taskChinhId == proj.id || (it.taskChinhId == null && it.safeTaskChinhTitle.isNotBlank() && it.safeTaskChinhTitle.equals(proj.safeTitle, ignoreCase = true)) }
                val computedProg = proj.computedProgress(pTasks)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onProjectClick(proj) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorderColor))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AccentPurpleLight
                            ) {
                                Text(
                                    text = proj.safePhongBan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryPurple,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            Text(
                                text = "$computedProg%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryPurple
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = proj.safeTitle,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${str.labelLead} ${proj.safeResponsiblePerson}",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                            Text(
                                text = CalendarUtils.formatDisplayRange(proj.safeStartDate, proj.safeEndDate, lang),
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { (computedProg / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = PrimaryPurple,
                            trackColor = PillBg
                        )
                    }
                }
            }
        }

        // 2. Phân mục: Công việc chi tiết trong ngày
        if (dayTasks.isNotEmpty()) {
            item {
                Text(
                    text = "${str.sectionDaySubtasks} (${dayTasks.count { it.isCompleted }}/${dayTasks.size} ${str.labelDoneSuffix}):",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
            }

            items(dayTasks, key = { it.id }) { task ->
                val parentProj = taskChinhList.find { it.id == task.taskChinhId }
                val canEdit = isTaskRelatedToUser(task, parentProj, resolvedEmail, repository.getSavedUserName())
                PersonalTaskCard(
                    task = task,
                    canEdit = canEdit,
                    onToggle = {
                        if (canEdit) {
                            coroutineScope.launch { repository.toggleTaskCompletion(task.id) }
                        }
                    },
                    onClick = { onTaskClick(task) }
                )
            }
        }

        // Trường hợp ngày không có cả dự án lẫn việc chi tiết
        if (dayProjects.isEmpty() && dayTasks.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorderColor))
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (searchQuery.isNotBlank()) str.emptySearchTitle else str.calendarNoTasks,
                            fontSize = 13.sp,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

/**
 * TAB 4: CÀI ĐẶT & HỒ SƠ (SETTINGS)
 */
@Composable
fun SettingsTab(
    repository: TaskRepository,
    userEmail: String,
    appLang: String,
    onSelectLang: (String) -> Unit,
    onLogout: () -> Unit
) {
    val str = AppStrings.of(LocalAppLanguage.current)
    val users by repository.users.collectAsState()
    val curUser = users.find { it.email.equals(userEmail, ignoreCase = true) }
    val displayName = curUser?.name ?: if (userEmail.contains("@")) userEmail.substringBefore("@").replace(".", " ") else str.defaultUserName
    val displayDept = curUser?.phongBan ?: "SeaZen Tech"
    val isVi = appLang == "vi"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            Text(
                text = str.settingsTitle,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Ho so nguoi dung
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = PrimaryPurple,
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = "SZ", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = displayName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = userEmail,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AccentPurpleLight
                        ) {
                            Text(
                                text = displayDept,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = PrimaryPurple,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // Cai dat Ngon ngu
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = str.sectionLanguage,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectLang("vi") },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isVi) PrimaryPurple else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Tiếng Việt",
                                    fontSize = 12.sp,
                                    fontWeight = if (isVi) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isVi) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectLang("en") },
                            shape = RoundedCornerShape(8.dp),
                            color = if (!isVi) PrimaryPurple else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "English",
                                    fontSize = 12.sp,
                                    fontWeight = if (!isVi) FontWeight.Bold else FontWeight.Normal,
                                    color = if (!isVi) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Nut Dang xuat
        item {
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626))
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(str.actionLogout)
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

/**
 * Thanh điều hướng nổi bo tròn phía dưới (Floating Bottom Nav) với 4 Tab + nút Tạo việc chính giữa
 */
@Composable
fun FloatingAppNavBar(
    selectedTab: Int,
    onTabSelect: (Int) -> Unit,
    onAddClick: () -> Unit
) {
    val str = AppStrings.of(LocalAppLanguage.current)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp, start = 20.dp, end = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color.White,
            shadowElevation = 6.dp,
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorderColor))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tab 0: Cá nhân
                IconButton(onClick = { onTabSelect(0) }) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = str.navMyTasks,
                        tint = if (selectedTab == 0) PrimaryPurple else TextMuted
                    )
                }

                // Tab 1: Phòng ban
                IconButton(onClick = { onTabSelect(1) }) {
                    Icon(
                        imageVector = Icons.Default.Apartment,
                        contentDescription = str.navDepartments,
                        tint = if (selectedTab == 1) PrimaryPurple else TextMuted
                    )
                }

                // Nút Tạo việc trung tâm màu tím
                Surface(
                    onClick = onAddClick,
                    shape = CircleShape,
                    color = PrimaryPurple,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, contentDescription = str.actionAdd, tint = Color.White)
                    }
                }

                // Tab 2: Lịch
                IconButton(onClick = { onTabSelect(2) }) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = str.navCalendar,
                        tint = if (selectedTab == 2) PrimaryPurple else TextMuted
                    )
                }

                // Tab 3: Cài đặt
                IconButton(onClick = { onTabSelect(3) }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = str.navSettings,
                        tint = if (selectedTab == 3) PrimaryPurple else TextMuted
                    )
                }
            }
        }
    }
}

@Composable
fun FilterTabPill(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) PrimaryDark else PillBg
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) Color.White else TextDark
        )
    }
}

@Composable
fun PersonalTaskCard(
    task: TaskItem,
    canEdit: Boolean = true,
    onToggle: () -> Unit,
    onClick: () -> Unit = {}
) {
    val lang = LocalAppLanguage.current
    val str = AppStrings.of(lang)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorderColor)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (task.isCompleted) (if (canEdit) PrimaryPurple else Color(0xFF16A34A)) else Color.Transparent)
                    .border(1.5.dp, if (task.isCompleted) (if (canEdit) PrimaryPurple else Color(0xFF16A34A)) else Color(0xFF94A3B8), CircleShape)
                    .then(if (canEdit) Modifier.clickable { onToggle() } else Modifier),
                contentAlignment = Alignment.Center
            ) {
                if (task.isCompleted) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (task.safeTaskChinhTitle.isNotBlank()) {
                    Text(
                        text = task.safeTaskChinhTitle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryPurple,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                Text(
                    text = task.safeTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (task.isCompleted) TextMuted else TextDark,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val isOverdue = task.isOverdue()
                    val dateRange = CalendarUtils.formatDisplayRange(task.safeStartDate, task.safeEndDate, lang)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isOverdue) Color(0xFFFEE2E2) else PillBg
                    ) {
                        Text(
                            text = if (isOverdue) "${str.tagOverdue} • $dateRange" else dateRange,
                            fontSize = 10.sp,
                            fontWeight = if (isOverdue) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isOverdue) Color(0xFFDC2626) else TextMuted,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (task.safePriority.lowercase() in listOf("urgent", "khẩn cấp", "high")) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFEE2E2)
                        ) {
                            Text(
                                text = str.tagUrgent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (task.safeAssigneeName.isNotBlank()) {
                        Text(text = "• ${task.safeAssigneeName}", fontSize = 11.sp, color = TextMuted, maxLines = 1)
                    }
                }

                if (task.safeGhiChu.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = task.safeGhiChu,
                        fontSize = 11.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

fun isTaskRelatedToUser(
    task: TaskItem,
    parentProject: TaskChinhItem?,
    currentUserEmail: String,
    currentUserName: String
): Boolean {
    if (com.seazen.task.widget.SeaZenWidgetProvider.isMyTask(task, currentUserEmail, currentUserName)) {
        return true
    }
    if (parentProject != null) {
        val myEmail = currentUserEmail.trim().lowercase()
        val pRespEmail = parentProject.safeResponsibleEmail.trim().lowercase()
        val pCreatorEmail = parentProject.safeCreatorEmail.trim().lowercase()
        if (myEmail.isNotBlank()) {
            if (pRespEmail == myEmail || pCreatorEmail == myEmail) return true
        }
    }
    return false
}

/**
 * Modal Bottom Sheet xem chi tiết Task
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailBottomSheet(
    task: TaskItem,
    parentProject: TaskChinhItem? = null,
    canEdit: Boolean = true,
    onDismiss: () -> Unit,
    onToggle: () -> Unit,
    onOpenParentProject: ((Long) -> Unit)? = null
) {
    val lang = LocalAppLanguage.current
    val str = AppStrings.of(lang)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    val effectiveParentTitle = task.safeTaskChinhTitle.ifBlank { parentProject?.safeTitle ?: "" }
    val effectiveParentId = task.taskChinhId ?: parentProject?.id
    val effectiveDept = task.safePhongBan.ifBlank { parentProject?.safePhongBan ?: "" }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (task.isCompleted) Color(0xFFDCFCE7) else AccentPurpleLight
                    ) {
                        Text(
                            text = if (task.isCompleted) str.tagCompleted else str.statusInProgress,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (task.isCompleted) Color(0xFF16A34A) else PrimaryPurple,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (!canEdit) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF1F5F9)
                        ) {
                            Text(
                                text = if (lang == "en") "Read-Only" else "Chỉ xem",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                if (task.isOverdue()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFEE2E2)
                    ) {
                        Text(
                            text = str.tagOverdue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFDC2626),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Text(
                text = task.safeTitle,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
                lineHeight = 24.sp
            )

            if (effectiveParentTitle.isNotBlank() || effectiveParentId != null) {
                Surface(
                    onClick = {
                        if (effectiveParentId != null && onOpenParentProject != null) {
                            onOpenParentProject(effectiveParentId)
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = PillBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = str.labelBelongsToProject, fontSize = 11.sp, color = TextMuted)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = effectiveParentTitle.ifBlank { "Project #${effectiveParentId}" },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryPurple
                            )
                        }
                        if (effectiveParentId != null && onOpenParentProject != null) {
                            Icon(Icons.Default.ArrowForward, contentDescription = str.actionViewParentProject, tint = PrimaryPurple, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow(label = str.labelTimeRange, value = CalendarUtils.formatDisplayRange(task.safeStartDate, task.safeEndDate, lang))
                if (task.safeAssigneeName.isNotBlank()) {
                    val assigneeText = if (task.safeAssigneeEmail.isNotBlank()) {
                        "${task.safeAssigneeName} (${task.safeAssigneeEmail})"
                    } else {
                        task.safeAssigneeName
                    }
                    DetailRow(label = str.labelAssignee, value = assigneeText)
                }
                if (effectiveDept.isNotBlank()) {
                    DetailRow(label = str.tagDepartment, value = effectiveDept)
                }
                DetailRow(label = str.labelPriority, value = if (task.safePriority.lowercase() in listOf("urgent", "khẩn cấp", "high")) str.tagUrgent else str.tagNormal)
            }

            if (task.safeGhiChu.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PillBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "${str.labelNotes}:", fontSize = 11.sp, color = TextMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = task.safeGhiChu, fontSize = 13.sp, color = TextDark)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (canEdit) {
                Button(
                    onClick = onToggle,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (task.isCompleted) TextMuted else PrimaryPurple)
                ) {
                    Text(
                        text = if (task.isCompleted) str.actionMarkPending else str.actionMarkCompleted,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = Color(0xFFF1F5F9),
                        disabledContentColor = Color(0xFF94A3B8)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (lang == "en") "Read-Only (No edit permission)" else "Chỉ xem (Không có quyền sửa)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Modal Bottom Sheet xem chi tiết Dự án / TaskChinh
 * Cho phép bấm vào công việc chính để xem chi tiết và thêm công việc con vào
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddChildTaskBottomSheet(
    projectTitle: String,
    currentUserEmail: String = "",
    users: List<SeaZenUser> = emptyList(),
    defaultStartDate: String = "",
    defaultEndDate: String = "",
    onDismiss: () -> Unit,
    onAdd: (title: String, sDate: String, eDate: String, name: String, email: String) -> Unit
) {
    val lang = LocalAppLanguage.current
    val str = AppStrings.of(lang)
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    var title by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(defaultStartDate.ifBlank { LocalDate.now().toString() }) }
    var endDate by remember { mutableStateOf(defaultEndDate.ifBlank { LocalDate.now().plusDays(2).toString() }) }
    var selectedAssignee by remember(users, currentUserEmail) {
        val found = users.find { it.email.equals(currentUserEmail, ignoreCase = true) }
            ?: users.find { it.name.contains("Sang", ignoreCase = true) }
            ?: users.firstOrNull()
        mutableStateOf(found)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = str.titleAddSubtask,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    if (projectTitle.isNotBlank()) {
                        Text(
                            text = "${str.labelParentProject}: $projectTitle",
                            fontSize = 12.sp,
                            color = PrimaryPurple,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = str.actionClose, tint = TextMuted)
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("${str.labelTaskTitle} *", fontSize = 12.sp) },
                placeholder = { Text(str.fieldTaskTitle, fontSize = 12.sp, color = TextMuted) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = PrimaryPurple,
                    unfocusedBorderColor = CardBorderColor
                ),
                shape = RoundedCornerShape(8.dp)
            )

            SingleUserSelector(
                label = "${str.labelAssignee} *",
                selectedUser = selectedAssignee,
                users = users,
                onSelect = { selectedAssignee = it }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = startDate,
                    onValueChange = { startDate = it },
                    label = { Text(str.labelStartDate, fontSize = 11.sp) },
                    trailingIcon = {
                        IconButton(onClick = {
                            val init = parseLocalDate(startDate) ?: LocalDate.now()
                            CalendarUtils.showDatePicker(context, init) { picked ->
                                startDate = picked.toString()
                            }
                        }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = PrimaryPurple, modifier = Modifier.size(18.dp))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = endDate,
                    onValueChange = { endDate = it },
                    label = { Text(str.labelEndDate, fontSize = 11.sp) },
                    trailingIcon = {
                        IconButton(onClick = {
                            val init = parseLocalDate(endDate) ?: LocalDate.now()
                            CalendarUtils.showDatePicker(context, init) { picked ->
                                endDate = picked.toString()
                            }
                        }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = PrimaryPurple, modifier = Modifier.size(18.dp))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onAdd(
                            title.trim(),
                            startDate.trim(),
                            endDate.trim(),
                            selectedAssignee?.name ?: "Sang Nguyễn",
                            selectedAssignee?.email ?: currentUserEmail.ifBlank { "sang.nguyen@seazen.com.vn" }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                enabled = title.isNotBlank()
            ) {
                Text(text = str.actionAdd, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailBottomSheet(
    project: TaskChinhItem,
    childTasks: List<TaskItem>,
    currentUserEmail: String = "",
    users: List<SeaZenUser> = emptyList(),
    onDismiss: () -> Unit,
    onToggleChild: (Long) -> Unit,
    onTaskClick: ((TaskItem) -> Unit)? = null,
    onRequestExtension: ((newDate: String, reason: String) -> Unit)? = null,
    onAddChildTask: (title: String, sDate: String, eDate: String, name: String, email: String) -> Unit
) {
    val lang = LocalAppLanguage.current
    val str = AppStrings.of(lang)
    val context = LocalContext.current
    val displayProgress = project.computedProgress(childTasks)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    var showExtensionDialog by remember { mutableStateOf(false) }
    var extNewDate by remember(project) {
        val curEnd = parseLocalDate(project.safeEndDate) ?: LocalDate.now()
        mutableStateOf(curEnd.plusDays(3).toString())
    }
    var extReason by remember { mutableStateOf("") }
    var showAddChildSheet by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AccentPurpleLight
                    ) {
                        Text(
                            text = project.safePhongBan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryPurple,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = PillBg
                    ) {
                        Text(
                            text = project.safePhanLoaiTask,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextDark,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = "${str.labelProgress}: $displayProgress%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryPurple
                )
            }

            Text(
                text = project.safeTitle,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
                lineHeight = 24.sp
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailRow(label = str.labelProjectLead, value = "${project.safeResponsiblePerson} (${project.safeResponsibleEmail})")
                DetailRow(label = str.labelTimeRange, value = CalendarUtils.formatDisplayRange(project.safeStartDate, project.safeEndDate, lang))
                DetailRow(label = str.labelClassification, value = project.safePhanLoaiTask)
                DetailRow(label = str.labelJobNature, value = project.safePhanLoaiViec)
                val liveStatus = if (childTasks.isNotEmpty() && childTasks.all { it.isCompleted }) str.tagCompleted else project.safeTrangThai
                DetailRow(label = str.labelStatus, value = liveStatus)
                if (project.safeCreatorName.isNotBlank()) {
                    DetailRow(label = str.labelAssignedBy, value = "${project.safeCreatorName} (${project.safeCreatorEmail})")
                }
                if (project.safeCcName.isNotBlank()) {
                    DetailRow(label = str.labelCc, value = "${project.safeCcName} (${project.safeCcEmail})")
                }
                if (project.safeNewEndDate.isNotBlank()) {
                    DetailRow(label = str.labelNewDeadline, value = "${project.safeNewEndDate} (${project.safeExtReason})")
                }
            }

            if (project.safeGhiChu.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PillBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "${str.labelNotes}:", fontSize = 11.sp, color = TextMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = project.safeGhiChu, fontSize = 13.sp, color = TextDark)
                    }
                }
            }

            LinearProgressIndicator(
                progress = { (displayProgress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = PrimaryPurple,
                trackColor = PillBg
            )

            // Khu vực Trạng thái Dời hạn hoặc Nút Đăng ký Dời Hạn
            if (project.safeTrangThaiDuyetHan == "Chờ duyệt" || project.safeNewEndDate.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFEF3C7),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFF59E0B))),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${str.labelExtensionRequest} ${project.safeTrangThaiDuyetHan.ifBlank { str.statusPendingApproval }}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E)
                            )
                            Text(
                                text = "${str.labelDue} ${project.safeNewEndDate} • ${str.labelReason} ${project.safeExtReason.ifBlank { str.labelNoReason }}",
                                fontSize = 11.sp,
                                color = Color(0xFFB45309)
                            )
                        }
                    }
                }
            } else if (onRequestExtension != null) {
                OutlinedButton(
                    onClick = { showExtensionDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryPurple)
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = str.actionExtendDeadline, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Tiêu đề Công việc chi tiết kèm nút Thêm công việc con
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${str.sectionProjectSubtasks} (${childTasks.count { it.isCompleted }}/${childTasks.size} ${str.labelDoneSuffix}):",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )

                Surface(
                    onClick = { showAddChildSheet = true },
                    shape = RoundedCornerShape(8.dp),
                    color = AccentPurpleLight
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = PrimaryPurple,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = str.actionAddSubtask,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryPurple
                        )
                    }
                }
            }

            // Hộp thoại Đăng ký dời hạn
            if (showExtensionDialog) {
                AlertDialog(
                    onDismissRequest = { showExtensionDialog = false },
                    title = { Text(str.actionExtendDeadline, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "${str.labelDue} ${project.safeEndDate.ifBlank { "--" }}",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                            OutlinedTextField(
                                value = extNewDate,
                                onValueChange = { extNewDate = it },
                                label = { Text("${str.labelNewDeadline} (YYYY-MM-DD) *", fontSize = 11.sp) },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        val init = parseLocalDate(extNewDate) ?: LocalDate.now().plusDays(3)
                                        CalendarUtils.showDatePicker(context, init) { picked ->
                                            extNewDate = picked.toString()
                                        }
                                    }) {
                                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = PrimaryPurple, modifier = Modifier.size(18.dp))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                            OutlinedTextField(
                                value = extReason,
                                onValueChange = { extReason = it },
                                label = { Text("${str.labelReason} *", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (extNewDate.isNotBlank() && extReason.isNotBlank()) {
                                    onRequestExtension?.invoke(extNewDate.trim(), extReason.trim())
                                    showExtensionDialog = false
                                }
                            },
                            enabled = extNewDate.isNotBlank() && extReason.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(str.actionConfirm, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showExtensionDialog = false }) {
                            Text(str.actionCancel, color = TextMuted)
                        }
                    },
                    containerColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                )
            }

            // Hiển thị modal Thêm công việc con riêng biệt chuẩn PowerApp
            if (showAddChildSheet) {
                AddChildTaskBottomSheet(
                    projectTitle = project.safeTitle,
                    currentUserEmail = currentUserEmail,
                    users = users,
                    defaultStartDate = project.safeStartDate,
                    defaultEndDate = project.safeEndDate,
                    onDismiss = { showAddChildSheet = false },
                    onAdd = { cTitle, cStart, cEnd, cName, cEmail ->
                        showAddChildSheet = false
                        onAddChildTask(cTitle, cStart, cEnd, cName, cEmail)
                    }
                )
            }

            // Danh sách công việc con - Sử dụng Column thay vì nested LazyColumn để tránh lỗi jitter / che khuất khi bàn phím bật lên
            if (childTasks.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    childTasks.forEach { ct ->
                        val canEditChild = isTaskRelatedToUser(ct, project, currentUserEmail, users.find { it.email.equals(currentUserEmail, ignoreCase = true) }?.name ?: "")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onTaskClick?.invoke(ct) }
                                .background(PillBg)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = ct.isCompleted,
                                onCheckedChange = { if (canEditChild) onToggleChild(ct.id) },
                                enabled = canEditChild,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = PrimaryPurple,
                                    disabledCheckedColor = Color(0xFF94A3B8),
                                    disabledUncheckedColor = Color(0xFFCBD5E1)
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = ct.safeTitle,
                                        fontSize = 12.sp,
                                        color = if (ct.isCompleted) TextMuted else TextDark,
                                        textDecoration = if (ct.isCompleted) TextDecoration.LineThrough else null,
                                        modifier = Modifier.weight(1f, fill = false),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (ct.safePriority.lowercase() in listOf("urgent", "khẩn cấp", "high")) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFFEE2E2)
                                        ) {
                                            Text(
                                                text = str.tagUrgent,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFDC2626),
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                if (ct.safeAssigneeName.isNotBlank() || ct.safeEndDate.isNotBlank()) {
                                    Text(
                                        text = "${ct.safeAssigneeName} • ${CalendarUtils.formatDisplayRange(ct.safeStartDate, ct.safeEndDate, lang)}",
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )
                                }
                                if (ct.safeGhiChu.isNotBlank()) {
                                    Text(
                                        text = ct.safeGhiChu,
                                        fontSize = 10.sp,
                                        color = TextMuted,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = str.noSubtasks,
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextMuted,
            modifier = Modifier.weight(0.38f)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextDark,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.62f)
        )
    }
}

/**
 * Modal Bottom Sheet tạo công việc
 */
/**
 * Modal Bottom Sheet tạo công việc chuẩn PowerApp SeaZen
 * Hỗ trợ tạo CÔNG VIỆC CHÍNH (8 trường nghiệp vụ) và CÔNG VIỆC CON (4 trường nghiệp vụ)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskBottomSheet(
    selectedDate: LocalDate,
    taskChinhList: List<TaskChinhItem>,
    currentUserEmail: String = "",
    users: List<SeaZenUser> = emptyList(),
    onDismiss: () -> Unit,
    onCreateChiTiet: (
        title: String,
        parentId: Long?,
        parentTitle: String,
        sDate: String,
        eDate: String,
        name: String,
        email: String
    ) -> Unit,
    onCreateChinh: (
        title: String,
        sDate: String,
        eDate: String,
        person: String,
        email: String,
        dept: String,
        phanLoaiTask: String,
        phanLoaiViec: String,
        ccName: String,
        ccEmail: String
    ) -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val str = AppStrings.of(lang)
    val defaultDateStr = selectedDate.toString()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    var isChildTaskMode by remember { mutableStateOf(false) }

    // Common fields
    var title by remember { mutableStateOf("") }
    var startDate by remember(selectedDate) { mutableStateOf(defaultDateStr) }
    var endDate by remember(selectedDate) { mutableStateOf(defaultDateStr) }

    // Child task fields
    var selectedChildAssignee by remember(users, currentUserEmail) {
        val found = users.find { it.email.equals(currentUserEmail, ignoreCase = true) }
            ?: users.find { it.name.contains("Sang", ignoreCase = true) }
            ?: users.firstOrNull()
        mutableStateOf(found)
    }
    var selectedTaskChinhId by remember { mutableStateOf<Long?>(taskChinhList.firstOrNull()?.id) }
    var selectedTaskChinhTitle by remember { mutableStateOf(taskChinhList.firstOrNull()?.safeTitle ?: "") }
    var activeDropdown by remember { mutableStateOf<String?>(null) }

    // Main task / project fields
    var selectedResponsibleUser by remember(users, currentUserEmail) {
        val found = users.find { it.email.equals(currentUserEmail, ignoreCase = true) }
            ?: users.find { it.name.contains("Sang", ignoreCase = true) }
            ?: users.firstOrNull()
        mutableStateOf(found)
    }
    var selectedCcUsers by remember {
        mutableStateOf<List<SeaZenUser>>(emptyList())
    }

    val curUser = remember(users, currentUserEmail) {
        users.find { it.email.equals(currentUserEmail, ignoreCase = true) }
            ?: users.find { it.name.contains("Sang", ignoreCase = true) }
    }
    val personalDept = curUser?.phongBan?.ifBlank { "SeaZen Tech" } ?: "SeaZen Tech"

    val deptOptions = listOf(
        "SeaZen Tech",
        "C&B",
        "Marketing",
        "Kinh Doanh",
        "Pháp Lý",
        "Kế Toán",
        "Điều Hành",
        "Tuyển Dụng",
        "Đào Tạo",
        "Hành Chính",
        "HCNS",
        "Vận Hành"
    )

    var selectedDept by remember(personalDept) {
        mutableStateOf(if (deptOptions.contains(personalDept)) personalDept else "SeaZen Tech")
    }

    val phanLoaiTaskOptions = listOf("Phòng ban", "Công ty")
    var selectedPhanLoaiTask by remember { mutableStateOf("Phòng ban") }

    val phanLoaiViecOptions = listOf(
        "Công việc tháng",
        "CSDL",
        "CSHT",
        "Họp giao ban tháng",
        "Khách Hàng",
        "Ngân sách",
        "OneSeaZen",
        "Phần mềm & Hệ thống",
        "SOP",
        "Truyền thông",
        "Xây Dựng CSDL",
        "Xây dựng app",
        "Xây dựng báo giá dịch vụ",
        "Xây dựng công cụ AI",
        "Yêu cầu khách hàng"
    )
    var selectedPhanLoaiViec by remember { mutableStateOf("Công việc tháng") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isChildTaskMode) str.titleAddSubtask else str.titleAddMainProject,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = str.actionClose, tint = TextMuted)
                }
            }

            // Tab chọn loại công việc (Công việc chính / Công việc con)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(PillBg)
                    .padding(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (!isChildTaskMode) Color.White else Color.Transparent)
                        .clickable { isChildTaskMode = false }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = str.tabMainProject,
                        fontSize = 12.sp,
                        fontWeight = if (!isChildTaskMode) FontWeight.Bold else FontWeight.Normal,
                        color = if (!isChildTaskMode) PrimaryPurple else TextMuted
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isChildTaskMode) Color.White else Color.Transparent)
                        .clickable { isChildTaskMode = true }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = str.tabSubTask,
                        fontSize = 12.sp,
                        fontWeight = if (isChildTaskMode) FontWeight.Bold else FontWeight.Normal,
                        color = if (isChildTaskMode) PrimaryPurple else TextMuted
                    )
                }
            }

            if (isChildTaskMode) {
                // CHẾ ĐỘ THÊM CÔNG VIỆC CON
                SearchableDropdown(
                    label = "${str.labelBelongsToMainTask} *",
                    selectedValue = selectedTaskChinhTitle,
                    options = taskChinhList.map { it.safeTitle },
                    onSelect = { chosenTitle ->
                        selectedTaskChinhTitle = chosenTitle
                        selectedTaskChinhId = taskChinhList.find { it.safeTitle == chosenTitle }?.id
                    },
                    placeholder = str.placeholderSelectParentProject,
                    searchPlaceholder = "Tìm dự án chính...",
                    expanded = (activeDropdown == "task_chinh"),
                    onExpandedChange = { activeDropdown = if (it) "task_chinh" else null }
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("${str.fieldTaskTitle} *", fontSize = 12.sp) },
                    placeholder = { Text(str.placeholderDetailTask, fontSize = 12.sp, color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    shape = RoundedCornerShape(8.dp)
                )

                SingleUserSelector(
                    label = "${str.fieldAssignee} *",
                    selectedUser = selectedChildAssignee,
                    users = users,
                    onSelect = { selectedChildAssignee = it }
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it },
                        label = { Text(str.labelStartDate, fontSize = 11.sp) },
                        trailingIcon = {
                            IconButton(onClick = {
                                val init = parseLocalDate(startDate) ?: LocalDate.now()
                                CalendarUtils.showDatePicker(context, init) { picked ->
                                    startDate = picked.toString()
                                }
                            }) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = PrimaryPurple, modifier = Modifier.size(18.dp))
                            }
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = endDate,
                        onValueChange = { endDate = it },
                        label = { Text(str.labelEndDate, fontSize = 11.sp) },
                        trailingIcon = {
                            IconButton(onClick = {
                                val init = parseLocalDate(endDate) ?: LocalDate.now()
                                CalendarUtils.showDatePicker(context, init) { picked ->
                                    endDate = picked.toString()
                                }
                            }) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = PrimaryPurple, modifier = Modifier.size(18.dp))
                            }
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            onCreateChiTiet(
                                title.trim(),
                                selectedTaskChinhId,
                                selectedTaskChinhTitle,
                                startDate.trim(),
                                endDate.trim(),
                                selectedChildAssignee?.name ?: "Sang Nguyễn",
                                selectedChildAssignee?.email ?: currentUserEmail.ifBlank { "sang.nguyen@seazen.com.vn" }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                    enabled = title.isNotBlank()
                ) {
                    Text(text = str.actionCreate, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            } else {
                // CHẾ ĐỘ THÊM CÔNG VIỆC CHÍNH (Chuẩn PowerApp: 8 trường chuẩn xác)
                // Trường 1: Tên công việc *
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("${str.fieldTaskTitle} *", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    shape = RoundedCornerShape(8.dp)
                )

                // Trường 2: Cấp giao việc * (Phòng ban, Công ty)
                SearchableDropdown(
                    label = "${str.labelClassification} *",
                    selectedValue = selectedPhanLoaiTask,
                    options = phanLoaiTaskOptions,
                    onSelect = { selectedPhanLoaiTask = it },
                    placeholder = "Chọn cấp giao việc...",
                    searchPlaceholder = "Tìm cấp giao việc...",
                    expanded = (activeDropdown == "phan_loai_task"),
                    onExpandedChange = { activeDropdown = if (it) "phan_loai_task" else null }
                )

                // Trường 3: Phân loại công việc * (Công việc tháng, CSDL, ...)
                SearchableDropdown(
                    label = "${str.labelJobNature} *",
                    selectedValue = selectedPhanLoaiViec,
                    options = phanLoaiViecOptions,
                    onSelect = { selectedPhanLoaiViec = it },
                    placeholder = "Chọn phân loại...",
                    searchPlaceholder = "Tìm phân loại...",
                    expanded = (activeDropdown == "phan_loai_viec"),
                    onExpandedChange = { activeDropdown = if (it) "phan_loai_viec" else null }
                )

                // Trường 4: Người phụ trách *
                SingleUserSelector(
                    label = "${str.labelProjectLead} *",
                    selectedUser = selectedResponsibleUser,
                    users = users,
                    onSelect = { user ->
                        selectedResponsibleUser = user
                        if (user.phongBan.isNotBlank() && deptOptions.contains(user.phongBan)) {
                            selectedDept = user.phongBan
                        }
                    }
                )

                // Trường 5: Phòng ban * (Dropdown chuẩn SeaZen, mặc định là phòng ban cá nhân)
                SearchableDropdown(
                    label = "${str.tagDepartment} *",
                    selectedValue = selectedDept,
                    options = deptOptions,
                    onSelect = { selectedDept = it },
                    placeholder = "Chọn phòng ban...",
                    searchPlaceholder = "Tìm phòng ban...",
                    expanded = (activeDropdown == "dept"),
                    onExpandedChange = { activeDropdown = if (it) "dept" else null }
                )

                // Trường 6: CC Email
                MultiUserSelector(
                    label = str.labelCc,
                    selectedUsers = selectedCcUsers,
                    users = users,
                    onAddUser = { if (!selectedCcUsers.any { u -> u.id == it.id }) selectedCcUsers = selectedCcUsers + it },
                    onRemoveUser = { selectedCcUsers = selectedCcUsers.filter { u -> u.id != it.id } }
                )

                // Trường 7 & 8: Ngày bắt đầu & Ngày kết thúc
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it },
                        label = { Text(str.labelStartDate, fontSize = 11.sp) },
                        trailingIcon = {
                            IconButton(onClick = {
                                val init = parseLocalDate(startDate) ?: LocalDate.now()
                                CalendarUtils.showDatePicker(context, init) { picked ->
                                    startDate = picked.toString()
                                }
                            }) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = PrimaryPurple, modifier = Modifier.size(18.dp))
                            }
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = endDate,
                        onValueChange = { endDate = it },
                        label = { Text(str.labelEndDate, fontSize = 11.sp) },
                        trailingIcon = {
                            IconButton(onClick = {
                                val init = parseLocalDate(endDate) ?: LocalDate.now()
                                CalendarUtils.showDatePicker(context, init) { picked ->
                                    endDate = picked.toString()
                                }
                            }) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = PrimaryPurple, modifier = Modifier.size(18.dp))
                            }
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            onCreateChinh(
                                title.trim(),
                                startDate.trim(),
                                endDate.trim(),
                                selectedResponsibleUser?.name ?: "Sang Nguyễn",
                                selectedResponsibleUser?.email ?: currentUserEmail.ifBlank { "sang.nguyen@seazen.com.vn" },
                                selectedDept.trim(),
                                selectedPhanLoaiTask,
                                selectedPhanLoaiViec,
                                selectedCcUsers.joinToString("; ") { it.name },
                                selectedCcUsers.joinToString("; ") { it.email }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                    enabled = title.isNotBlank()
                ) {
                    Text(text = str.actionCreate, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

/**
 * Màn hình đăng nhập Microsoft 365
 */
/**
 * Màn hình Landing bắt đầu theo phong cách thiết kế hiện đại (Image 3)
 */
@Composable
fun LandingScreen(
    onGetStarted: () -> Unit
) {
    val lang = LocalAppLanguage.current
    val str = AppStrings.of(lang)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1B3B8B))
            .clickable { onGetStarted() }
            .padding(32.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "sz",
                    fontSize = 46.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = (-1).sp
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .size(10.dp)
                        .background(Color(0xFF38BDF8), CircleShape)
                )
                Text(
                    text = "task",
                    fontSize = 46.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = (-1).sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = str.landingSubtitle,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
        }

        Button(
            onClick = onGetStarted,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(52.dp)
                .padding(bottom = 4.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color(0xFF1B3B8B)
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Text(
                text = str.btnGetStarted,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B3B8B)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = Color(0xFF1B3B8B),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Màn hình Đăng nhập theo phong cách Image 3:
 * - Đăng nhập tài khoản mật khẩu
 * - Ở dưới là "— Hoặc đăng nhập bằng —" với nút Microsoft 365
 * - Không còn dữ liệu mẫu, không còn dán token thủ công
 */
@Composable
fun LoginScreen(
    repository: TaskRepository,
    onBackToLanding: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val str = AppStrings.of(lang)
    val coroutineScope = rememberCoroutineScope()

    var email by remember { mutableStateOf(repository.getSavedEmail().ifBlank { "sang.nguyen@seazen.com.vn" }) }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    var deviceUserCode by remember { mutableStateOf("") }
    var deviceVerificationUri by remember { mutableStateOf("") }
    var isWaitingDeviceLogin by remember { mutableStateOf(false) }

    fun performMicrosoftDeviceFlow() {
        isLoading = true
        errorMessage = ""
        coroutineScope.launch {
            val devRes = repository.authManager.requestDeviceCode()
            isLoading = false
            if (devRes != null) {
                deviceUserCode = devRes.userCode
                deviceVerificationUri = devRes.verificationUri
                isWaitingDeviceLogin = true
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("user_code", devRes.userCode))
                Toast.makeText(context, if (lang == "en") "Verification code copied: ${devRes.userCode}" else "Đã sao chép mã xác thực: ${devRes.userCode}", Toast.LENGTH_LONG).show()

                launch {
                    var polling = true
                    while (polling) {
                        kotlinx.coroutines.delay(devRes.interval * 1000)
                        val pollRes = repository.authManager.pollDeviceCodeToken(devRes.deviceCode)
                        if (pollRes.success && pollRes.accessToken != null) {
                            polling = false
                            repository.saveEmail(email)
                            repository.saveToken(pollRes.accessToken)
                            if (pollRes.refreshToken != null) repository.saveRefreshToken(pollRes.refreshToken)
                            repository.fetchAllFromSharePoint(pollRes.accessToken)
                            isWaitingDeviceLogin = false
                            onLoginSuccess()
                        } else if (pollRes.errorMessage?.contains("expired") == true) {
                            polling = false
                            isWaitingDeviceLogin = false
                            errorMessage = if (lang == "en") "Verification code expired, please try again." else "Mã xác thực đã hết hạn, vui lòng thử lại."
                        }
                    }
                }
            } else {
                errorMessage = if (lang == "en") "Unable to generate Device Code." else "Không thể tạo mã xác thực Device Code."
            }
        }
    }

    Scaffold(containerColor = Color.White) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar with back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackToLanding) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF1E293B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Logo Header (cignifi-style)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "sz",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1B3B8B),
                    letterSpacing = (-1).sp
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp, vertical = 2.dp)
                        .size(8.dp)
                        .background(Color(0xFF0284C7), CircleShape)
                )
                Text(
                    text = "task",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1B3B8B),
                    letterSpacing = (-1).sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Title "Login to your Account"
            Text(
                text = str.loginScreenTitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Email Input
            Text(
                text = str.fieldEmail,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF475569),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = { Text("sang.nguyen@seazen.com.vn", fontSize = 14.sp, color = Color(0xFF94A3B8)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF1B3B8B),
                    unfocusedBorderColor = Color(0xFFE2E8F0),
                    focusedContainerColor = Color(0xFFF8FAFC),
                    unfocusedContainerColor = Color(0xFFF8FAFC)
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(20.dp)
                    )
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password Input
            Text(
                text = str.fieldPassword,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF475569),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = { Text("••••••••", fontSize = 14.sp, color = Color(0xFF94A3B8)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF1B3B8B),
                    unfocusedBorderColor = Color(0xFFE2E8F0),
                    focusedContainerColor = Color(0xFFF8FAFC),
                    unfocusedContainerColor = Color(0xFFF8FAFC)
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )

            // MFA verification prompt if needed
            if (isWaitingDeviceLogin && deviceUserCode.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFEFF6FF),
                    border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = str.mfaTitle, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = deviceUserCode, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1B3B8B), letterSpacing = 3.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = str.mfaDesc,
                            fontSize = 11.sp,
                            color = Color(0xFF3B82F6),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("user_code", deviceUserCode))
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deviceVerificationUri))
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B3B8B)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(str.btnOpenMfa, fontSize = 12.sp, color = Color.White)
                        }
                    }
                }
            }

            // Error display
            if (errorMessage.isNotBlank() && !isWaitingDeviceLogin) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFEF2F2)
                ) {
                    Text(
                        text = errorMessage,
                        fontSize = 12.sp,
                        color = Color(0xFFDC2626),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Primary "Sign In" / "Đăng nhập" button
            Button(
                onClick = {
                    errorMessage = ""
                    if (email.isBlank() || password.isBlank()) {
                        errorMessage = str.loginErrorBlank
                        return@Button
                    }
                    isLoading = true
                    isWaitingDeviceLogin = false

                    coroutineScope.launch {
                        val res = repository.loginMicrosoft(email, password)
                        if (res.success && res.accessToken != null) {
                            repository.saveEmail(email)
                            repository.saveToken(res.accessToken)
                            if (res.refreshToken != null) {
                                repository.saveRefreshToken(res.refreshToken)
                            }
                            val foundUser = repository.users.value.find { it.email.equals(email, ignoreCase = true) }
                            if (foundUser != null) {
                                repository.saveUserName(foundUser.name)
                            }
                            repository.fetchAllFromSharePoint(res.accessToken)
                            isLoading = false
                            Toast.makeText(context, if (lang == "en") "Signed in successfully" else "Đăng nhập thành công", Toast.LENGTH_SHORT).show()
                            onLoginSuccess()
                        } else {
                            isLoading = false
                            if (res.isMfaRequired) {
                                performMicrosoftDeviceFlow()
                            } else {
                                errorMessage = res.errorMessage ?: if (lang == "en") "Sign in failed" else "Đăng nhập thất bại"
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B3B8B)),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(str.btnConnectingMicrosoft, color = Color.White, fontWeight = FontWeight.SemiBold)
                } else {
                    Text(
                        text = if (lang == "en") "Sign In" else "Đăng nhập",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Divider: — Or sign in with —
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE2E8F0))
                Text(
                    text = "  ${str.orSignInWith}  ",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE2E8F0))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Microsoft Login Card / Button
            OutlinedButton(
                onClick = { performMicrosoftDeviceFlow() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFF8FAFC)),
                enabled = !isLoading
            ) {
                MicrosoftLogoIcon()
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = str.btnSignInWithMicrosoft,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E293B)
                )
            }
        }
    }
}

@Composable
fun MicrosoftLogoIcon() {
    Column(
        modifier = Modifier.size(18.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(modifier = Modifier.size(8.dp).background(Color(0xFFF25022)))
            Box(modifier = Modifier.size(8.dp).background(Color(0xFF7FBA00)))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(modifier = Modifier.size(8.dp).background(Color(0xFF00A4EF)))
            Box(modifier = Modifier.size(8.dp).background(Color(0xFFFFB900)))
        }
    }
}
