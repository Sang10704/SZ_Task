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

        val repository = TaskRepository(applicationContext)

        setContent {
            var isDark by remember { mutableStateOf(repository.isDarkMode()) }
            var appLang by remember { mutableStateOf(repository.getAppLanguage()) }

            SZTaskTheme(isDark = isDark) {
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
                        isDark = isDark,
                        onToggleDark = {
                            isDark = it
                            repository.setDarkMode(it)
                        },
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

@Composable
fun CrashRecoveryScreen(
    errorMessage: String,
    onResetAndRestart: () -> Unit
) {
    val context = LocalContext.current
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
                        text = "Khôi phục trạng thái SZ Task",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFDC2626)
                    )
                    Text(
                        text = "Hệ thống tự động phát hiện và ngăn ngừa lỗi dừng đột ngột:",
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
                        Text("Khôi phục bộ nhớ & Khởi động lại", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("error_log", errorMessage))
                            Toast.makeText(context, "Đã sao chép chi tiết lỗi", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Sao chép chi tiết lỗi")
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
fun SZTaskTheme(isDark: Boolean = false, content: @Composable () -> Unit) {
    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = PrimaryPurple,
            onPrimary = Color.White,
            secondary = PrimaryPurpleDark,
            background = Color(0xFF0F172A),
            surface = Color(0xFF1E293B),
            surfaceVariant = Color(0xFF334155),
            outline = Color(0xFF475569)
        )
    } else {
        lightColorScheme(
            primary = PrimaryPurple,
            onPrimary = Color.White,
            secondary = PrimaryPurpleDark,
            background = BackgroundLight,
            surface = Color.White,
            surfaceVariant = PillBg,
            outline = Color(0xFFE2E8F0)
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

@Composable
fun AppNavigator(
    repository: TaskRepository,
    intentState: MutableState<Intent?>,
    isDark: Boolean,
    onToggleDark: (Boolean) -> Unit,
    appLang: String,
    onSelectLang: (String) -> Unit
) {
    var isLoggedIn by remember { mutableStateOf(repository.isConfigured()) }

    if (isLoggedIn) {
        MainContainerScreen(
            repository = repository,
            intentState = intentState,
            isDark = isDark,
            onToggleDark = onToggleDark,
            appLang = appLang,
            onSelectLang = onSelectLang,
            onLogout = {
                repository.logout()
                isLoggedIn = false
            }
        )
    } else {
        LoginScreen(
            repository = repository,
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
    isDark: Boolean,
    onToggleDark: (Boolean) -> Unit,
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

    // Tu dong dong bo SharePoint khi mo ung dung
    LaunchedEffect(Unit) {
        repository.fetchAllFromSharePoint()
    }

    val tasks by repository.tasks.collectAsState()
    val taskChinhList by repository.taskChinhList.collectAsState()
    val users by repository.users.collectAsState()
    val userEmail = repository.getSavedEmail().lowercase().trim()

    // Xử lý sự kiện mở từ Widget
    val currentWidgetIntent = intentState.value
    LaunchedEffect(currentWidgetIntent, tasks, taskChinhList) {
        val targetIntent = currentWidgetIntent ?: return@LaunchedEffect
        val openTaskId = targetIntent.getLongExtra("open_task_id", -1L)
        val openProjectId = targetIntent.getLongExtra("open_project_id", -1L)
        val openTab = targetIntent.getIntExtra("open_tab", -1)
        val openCreate = targetIntent.getBooleanExtra("open_create_task", false)

        if (openTaskId > 0) {
            tasks.find { it.id == openTaskId }?.let { viewingTask = it }
        }
        if (openProjectId > 0) {
            taskChinhList.find { it.id == openProjectId }?.let { viewingProject = it }
        }
        if (openTab in 0..3) {
            selectedTab = openTab
        }
        if (openCreate) {
            targetCreateDate = LocalDate.now()
            showCreateBottomSheet = true
        }
        intentState.value = null
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
                onTabSelect = { selectedTab = it },
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
                        onProjectClick = { viewingProject = it },
                        onTaskClick = { viewingTask = it }
                    )
                    2 -> CalendarTab(
                        repository = repository,
                        tasks = tasks,
                        taskChinhList = taskChinhList,
                        onAddClick = { date ->
                            targetCreateDate = date
                            showCreateBottomSheet = true
                        },
                        onTaskClick = { viewingTask = it },
                        onProjectClick = { viewingProject = it }
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                SettingsTab(
                    repository = repository,
                    userEmail = userEmail,
                    isDark = isDark,
                    onToggleDark = onToggleDark,
                    appLang = appLang,
                    onSelectLang = onSelectLang,
                    onLogout = onLogout
                )
            }
        }
    }

    // Modal chi tiết Task
    viewingTask?.let { task ->
        val liveTask = tasks.find { it.id == task.id } ?: task
        val parentProj = taskChinhList.find { it.id == liveTask.taskChinhId }
        TaskDetailBottomSheet(
            task = liveTask,
            parentProject = parentProj,
            onDismiss = { viewingTask = null },
            onToggle = {
                coroutineScope.launch {
                    val res = repository.toggleTaskCompletion(liveTask.id)
                    if (res.isSuccess) {
                        viewingTask = liveTask.copy(isCompleted = !liveTask.isCompleted)
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
        val childTasks = tasks.filter { it.taskChinhId == liveProj.id }
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
                        Toast.makeText(context, "Đã gửi yêu cầu dời hạn thành công", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Lỗi dời hạn: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
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
                        Toast.makeText(context, "Đã thêm việc con vào: ${liveProj.safeTitle}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Lỗi: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
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
                        Toast.makeText(context, "Đã thêm công việc thành công", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Lỗi: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
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
                        Toast.makeText(context, "Đã tạo công việc chính mới", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Lỗi: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
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
    val coroutineScope = rememberCoroutineScope()
    var filterType by remember { mutableStateOf("today") } // "today", "upcoming", "overdue", "all"
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val myTasks = remember(tasks, userEmail) {
        if (userEmail.isNotBlank()) {
            tasks.filter { it.safeAssigneeEmail.lowercase().contains(userEmail) || it.safeAssigneeName.lowercase().contains("sang") }
        } else tasks
    }

    val today = LocalDate.now()
    val overdueCount = remember(myTasks, today) { myTasks.count { it.isOverdue(today) } }

    val displayTasks = remember(myTasks, filterType, searchQuery, today) {
        val base = when (filterType) {
            "today" -> myTasks.filter { it.isActiveOn(today) }
            "upcoming" -> myTasks.filter { !it.isCompleted }
            "overdue" -> myTasks.filter { it.isOverdue(today) }
            else -> myTasks
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

    val myProjects = remember(taskChinhList, userEmail) {
        val list = taskChinhList.filter { it.safeResponsibleEmail.lowercase().contains(userEmail) || it.safeResponsiblePerson.lowercase().contains("sang") }
        if (list.isNotEmpty()) list else taskChinhList.take(3)
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
                        placeholder = { Text("Tìm việc, dự án...", fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = PrimaryPurple, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Xóa", tint = TextMuted, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        searchQuery = ""
                        isSearchOpen = false
                    }) {
                        Text("Đóng", fontSize = 13.sp, color = PrimaryPurple, fontWeight = FontWeight.SemiBold)
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
                                text = "Xin chào, Sang Nguyễn",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Text(
                                text = "Chào mừng trở lại SZ Task",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }

                    IconButton(onClick = { isSearchOpen = true }) {
                        Icon(Icons.Default.Search, contentDescription = "Tìm kiếm", tint = TextDark)
                    }
                }
            }
        }

        // Khối Thống Kê Tổng Quan Hôm Nay (Daily Overview)
        item {
            val todayTasks = remember(myTasks, today) { myTasks.filter { it.isActiveOn(today) } }
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
                        Text(text = "Hôm nay", fontSize = 11.sp, color = TextMuted)
                    }

                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(CardBorderColor))

                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(text = "$doneToday/${todayTasks.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                        Text(text = "Đã xong", fontSize = 11.sp, color = TextMuted)
                    }

                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(CardBorderColor))

                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$overdueCount",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (overdueCount > 0) Color(0xFFDC2626) else TextMuted
                        )
                        Text(text = "Trễ hạn", fontSize = 11.sp, color = TextMuted)
                    }
                }
            }
        }

        // Section: Thẻ dự án trượt ngang (Horizontal Carousel - Ảnh 2)
        item {
            Column {
                Text(
                    text = "Dự án phụ trách",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(myProjects) { proj ->
                        val pTasks = tasks.filter { it.taskChinhId == proj.id }
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
                                            Text(text = "Tiến độ", fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
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
                FilterTabPill("Hôm nay", filterType == "today") { filterType = "today" }
                FilterTabPill("Cần làm", filterType == "upcoming") { filterType = "upcoming" }
                FilterTabPill(
                    text = if (overdueCount > 0) "Trễ hạn ($overdueCount)" else "Trễ hạn",
                    isSelected = filterType == "overdue"
                ) { filterType = "overdue" }
                FilterTabPill("Tất cả", filterType == "all") { filterType = "all" }
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
                        Text(text = "Không có công việc cá nhân trong danh mục này", fontSize = 13.sp, color = TextMuted)
                    }
                }
            }
        } else {
            items(displayTasks, key = { it.id }) { task ->
                PersonalTaskCard(
                    task = task,
                    onToggle = {
                        coroutineScope.launch {
                            repository.toggleTaskCompletion(task.id)
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
    onProjectClick: (TaskChinhItem) -> Unit,
    onTaskClick: (TaskItem) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedDept by remember { mutableStateOf("Tất cả") }
    var selectedType by remember { mutableStateOf("Tất cả") }
    var selectedStatus by remember { mutableStateOf("Tất cả") }
    var searchQuery by remember { mutableStateOf("") }

    val departments = listOf("Tất cả", "SeaZen Tech", "Điều Hành", "Tuyển Dụng", "Đào Tạo", "Marketing", "Hành Chính", "C&B")

    val filteredProjects = remember(taskChinhList, tasks, selectedDept, selectedType, selectedStatus, searchQuery) {
        taskChinhList.filter { p ->
            val matchDept = (selectedDept == "Tất cả" || p.safePhongBan.equals(selectedDept, ignoreCase = true))
            val matchType = (selectedType == "Tất cả" || p.safePhanLoaiTask.equals(selectedType, ignoreCase = true))
            val pTasks = tasks.filter { it.taskChinhId == p.id }
            val pProg = p.computedProgress(pTasks)
            val isProjOverdue = p.isOverdue() && pProg < 100
            val matchStatus = when (selectedStatus) {
                "Hoàn thành" -> pProg >= 100 || p.safeTrangThai.contains("Hoàn thành", ignoreCase = true)
                "Đang làm" -> pProg < 100 && !isProjOverdue
                "Trễ hạn" -> isProjOverdue
                else -> true
            }
            val matchSearch = searchQuery.isBlank() ||
                    p.safeTitle.contains(searchQuery, ignoreCase = true) ||
                    p.safeResponsiblePerson.contains(searchQuery, ignoreCase = true) ||
                    p.safePhongBan.contains(searchQuery, ignoreCase = true)
            matchDept && matchType && matchStatus && matchSearch
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
                    text = "Phòng ban & Công ty",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Text(
                    text = "Quản lý tiến độ công việc liên phòng ban trên SharePoint",
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
                placeholder = { Text("Tìm kiếm dự án, phòng ban, người chủ trì...", fontSize = 13.sp, color = TextMuted) },
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

        // Bộ lọc phòng ban (Horizontal Scroll)
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(departments) { dept ->
                    FilterTabPill(dept, selectedDept == dept) { selectedDept = dept }
                }
            }
        }

        // Bộ lọc trạng thái
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterTabPill("Tất cả", selectedStatus == "Tất cả") { selectedStatus = "Tất cả" }
                FilterTabPill("Đang làm", selectedStatus == "Đang làm") { selectedStatus = "Đang làm" }
                FilterTabPill("Trễ hạn", selectedStatus == "Trễ hạn") { selectedStatus = "Trễ hạn" }
                FilterTabPill("Đã xong", selectedStatus == "Hoàn thành") { selectedStatus = "Hoàn thành" }
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
                        Text(text = "Không tìm thấy công việc phù hợp với bộ lọc", fontSize = 13.sp, color = TextMuted)
                    }
                }
            }
        } else {
            items(filteredProjects, key = { it.id }) { proj ->
                DepartmentProjectCard(
                    project = proj,
                    childTasks = tasks.filter { it.taskChinhId == proj.id },
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
    onToggleChild: (Long) -> Unit,
    onClick: () -> Unit = {},
    onTaskClick: (TaskItem) -> Unit = {},
    onAddChildClick: () -> Unit = {}
) {
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
                                text = "Trễ hạn",
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
                    text = "Chủ trì: ${project.safeResponsiblePerson}",
                    fontSize = 11.sp,
                    color = TextMuted
                )
                Text(
                    text = CalendarUtils.formatDisplayRange(project.safeStartDate, project.safeEndDate),
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }

            if (project.safeCreatorName.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Giao việc: ${project.safeCreatorName}",
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
                    text = "Hạng mục chi tiết (${childTasks.count { it.isCompleted }}/${childTasks.size} xong):",
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
                            Checkbox(
                                checked = ct.isCompleted,
                                onCheckedChange = { onToggleChild(ct.id) },
                                colors = CheckboxDefaults.colors(checkedColor = PrimaryPurple)
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
                                                text = "Khẩn cấp",
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
                                        text = "${ct.safeAssigneeName} • ${CalendarUtils.formatDisplayRange(ct.safeStartDate, ct.safeEndDate)}",
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
                    text = "${childTasks.size} công việc con",
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
                        Text(text = "Thêm việc con", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryPurple)
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
    onAddClick: (LocalDate) -> Unit,
    onTaskClick: (TaskItem) -> Unit,
    onProjectClick: (TaskChinhItem) -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var currentYearMonth by remember { mutableStateOf(YearMonth.now()) }

    val dayTasks = remember(tasks, selectedDate) {
        tasks.filter { it.isActiveOn(selectedDate) }
    }
    val dayProjects = remember(taskChinhList, selectedDate) {
        taskChinhList.filter { it.isActiveOn(selectedDate) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Header chuyển tháng
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = CalendarUtils.formatHeaderDate(selectedDate),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Text(
                        text = CalendarUtils.formatMonthYear(currentYearMonth),
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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

        // Lưới lịch tháng
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorderColor))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    val weekDays = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
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
                                        val taskCount = remember(tasks, dayDate) { tasks.count { it.isActiveOn(dayDate) } }
                                        val projectCount = remember(taskChinhList, dayDate) { taskChinhList.count { it.isActiveOn(dayDate) } }

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
                                            val hasOverdue = remember(tasks, taskChinhList, dayDate) {
                                                tasks.filter { it.isActiveOn(dayDate) }.any { it.isOverdue() } ||
                                                taskChinhList.filter { it.isActiveOn(dayDate) }.any { it.isOverdue() }
                                            }
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

        // Tiêu đề ngày được chọn kèm nút thêm nhanh
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lịch ngày ${selectedDate.dayOfMonth}/${selectedDate.monthValue}",
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
                    text = "Dự án & Mục tiêu chính (${dayProjects.size}):",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
            }

            items(dayProjects, key = { "proj_${it.id}" }) { proj ->
                val pTasks = tasks.filter { it.taskChinhId == proj.id }
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
                                text = "Chủ trì: ${proj.safeResponsiblePerson}",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                            Text(
                                text = CalendarUtils.formatDisplayRange(proj.safeStartDate, proj.safeEndDate),
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
                    text = "Công việc chi tiết (${dayTasks.count { it.isCompleted }}/${dayTasks.size} xong):",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
            }

            items(dayTasks, key = { it.id }) { task ->
                PersonalTaskCard(
                    task = task,
                    onToggle = {
                        coroutineScope.launch { repository.toggleTaskCompletion(task.id) }
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
                        Text(text = "Không có công việc hoặc dự án nào trong ngày này", fontSize = 13.sp, color = TextMuted)
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
    isDark: Boolean,
    onToggleDark: (Boolean) -> Unit,
    appLang: String,
    onSelectLang: (String) -> Unit,
    onLogout: () -> Unit
) {
    val users by repository.users.collectAsState()
    val curUser = users.find { it.email.equals(userEmail, ignoreCase = true) }
    val displayName = curUser?.name ?: if (userEmail.contains("@")) userEmail.substringBefore("@").replace(".", " ") else "Người dùng SeaZen"
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
                text = if (isVi) "Cài đặt hệ thống" else "Settings",
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
                            color = if (isDark) Color(0xFF334155) else AccentPurpleLight
                        ) {
                            Text(
                                text = displayDept,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isDark) Color(0xFFA5B4FC) else PrimaryPurple,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // Cai dat Giao dien
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isVi) "Giao diện" else "Appearance",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isVi) (if (isDark) "Chế độ tối (Dark mode)" else "Chế độ sáng (Light mode)") else (if (isDark) "Dark mode" else "Light mode"),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = isDark,
                            onCheckedChange = onToggleDark,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PrimaryPurple
                            )
                        )
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
                        text = if (isVi) "Ngôn ngữ" else "Language",
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
                Text(if (isVi) "Đăng xuất tài khoản" else "Log out")
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
                        contentDescription = "Cá nhân",
                        tint = if (selectedTab == 0) PrimaryPurple else TextMuted
                    )
                }

                // Tab 1: Phòng ban
                IconButton(onClick = { onTabSelect(1) }) {
                    Icon(
                        imageVector = Icons.Default.Apartment,
                        contentDescription = "Phòng ban",
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
                        Icon(Icons.Default.Add, contentDescription = "Tạo việc", tint = Color.White)
                    }
                }

                // Tab 2: Lịch
                IconButton(onClick = { onTabSelect(2) }) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Lịch",
                        tint = if (selectedTab == 2) PrimaryPurple else TextMuted
                    )
                }

                // Tab 3: Cài đặt
                IconButton(onClick = { onTabSelect(3) }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Cài đặt",
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
    onToggle: () -> Unit,
    onClick: () -> Unit = {}
) {
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
                    .background(if (task.isCompleted) PrimaryPurple else Color.Transparent)
                    .border(1.5.dp, if (task.isCompleted) PrimaryPurple else Color(0xFF94A3B8), CircleShape)
                    .clickable { onToggle() },
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
                    val dateRange = CalendarUtils.formatDisplayRange(task.safeStartDate, task.safeEndDate)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isOverdue) Color(0xFFFEE2E2) else PillBg
                    ) {
                        Text(
                            text = if (isOverdue) "Trễ hạn • $dateRange" else dateRange,
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
                                text = "Khẩn cấp",
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

/**
 * Modal Bottom Sheet xem chi tiết Task
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailBottomSheet(
    task: TaskItem,
    parentProject: TaskChinhItem? = null,
    onDismiss: () -> Unit,
    onToggle: () -> Unit,
    onOpenParentProject: ((Long) -> Unit)? = null
) {
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
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (task.isCompleted) Color(0xFFDCFCE7) else AccentPurpleLight
                ) {
                    Text(
                        text = if (task.isCompleted) "Đã hoàn thành" else "Đang thực hiện",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (task.isCompleted) Color(0xFF16A34A) else PrimaryPurple,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                if (task.isOverdue()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFEE2E2)
                    ) {
                        Text(
                            text = "Trễ hạn",
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
                            Text(text = "Thuộc dự án / mục tiêu chính:", fontSize = 11.sp, color = TextMuted)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = effectiveParentTitle.ifBlank { "Dự án #${effectiveParentId}" },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryPurple
                            )
                        }
                        if (effectiveParentId != null && onOpenParentProject != null) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Xem dự án", tint = PrimaryPurple, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow(label = "Thời hạn thực hiện", value = CalendarUtils.formatDisplayRange(task.safeStartDate, task.safeEndDate))
                if (task.safeAssigneeName.isNotBlank()) {
                    val assigneeText = if (task.safeAssigneeEmail.isNotBlank()) {
                        "${task.safeAssigneeName} (${task.safeAssigneeEmail})"
                    } else {
                        task.safeAssigneeName
                    }
                    DetailRow(label = "Người thực hiện", value = assigneeText)
                }
                if (effectiveDept.isNotBlank()) {
                    DetailRow(label = "Phòng ban", value = effectiveDept)
                }
                DetailRow(label = "Độ ưu tiên", value = if (task.safePriority.lowercase() in listOf("urgent", "khẩn cấp", "high")) "Khẩn cấp" else "Bình thường")
            }

            if (task.safeGhiChu.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PillBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "Ghi chú / Chi tiết nội dung:", fontSize = 11.sp, color = TextMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = task.safeGhiChu, fontSize = 13.sp, color = TextDark)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (task.isCompleted) TextMuted else PrimaryPurple)
            ) {
                Text(
                    text = if (task.isCompleted) "Đánh dấu chưa hoàn thành" else "Đánh dấu hoàn thành",
                    fontWeight = FontWeight.SemiBold
                )
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
                        text = "THÊM CÔNG VIỆC CON",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    if (projectTitle.isNotBlank()) {
                        Text(
                            text = "Dự án: $projectTitle",
                            fontSize = 12.sp,
                            color = PrimaryPurple,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Đóng", tint = TextMuted)
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Công việc *", fontSize = 12.sp) },
                placeholder = { Text("Nhập công việc chi tiết", fontSize = 12.sp, color = TextMuted) },
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
                label = "Người phụ trách *",
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
                    label = { Text("Ngày bắt đầu", fontSize = 11.sp) },
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
                    label = { Text("Ngày kết thúc", fontSize = 11.sp) },
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
                Text(text = "Thêm", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
                    text = "Tiến độ: $displayProgress%",
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
                DetailRow(label = "Chủ trì dự án", value = "${project.safeResponsiblePerson} (${project.safeResponsibleEmail})")
                DetailRow(label = "Thời gian", value = CalendarUtils.formatDisplayRange(project.safeStartDate, project.safeEndDate))
                DetailRow(label = "Cấp độ phân loại", value = project.safePhanLoaiTask)
                DetailRow(label = "Tính chất công việc", value = project.safePhanLoaiViec)
                val liveStatus = if (childTasks.isNotEmpty() && childTasks.all { it.isCompleted }) "Hoàn thành" else project.safeTrangThai
                DetailRow(label = "Trạng thái", value = liveStatus)
                if (project.safeCreatorName.isNotBlank()) {
                    DetailRow(label = "Người giao việc", value = "${project.safeCreatorName} (${project.safeCreatorEmail})")
                }
                if (project.safeCcName.isNotBlank()) {
                    DetailRow(label = "Người nhận TB (CC)", value = "${project.safeCcName} (${project.safeCcEmail})")
                }
                if (project.safeNewEndDate.isNotBlank()) {
                    DetailRow(label = "Gia hạn mới", value = "${project.safeNewEndDate} (${project.safeExtReason})")
                }
            }

            if (project.safeGhiChu.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PillBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "Ghi chú / Mục tiêu dự án:", fontSize = 11.sp, color = TextMuted)
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
                                text = "Yêu cầu dời hạn: ${project.safeTrangThaiDuyetHan.ifBlank { "Chờ duyệt" }}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E)
                            )
                            Text(
                                text = "Hạn mới: ${project.safeNewEndDate} • Lý do: ${project.safeExtReason.ifBlank { "Không có lý do" }}",
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
                    Text(text = "Đăng ký dời hạn công việc", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Tiêu đề Công việc chi tiết kèm nút Thêm công việc con
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Công việc chi tiết (${childTasks.count { it.isCompleted }}/${childTasks.size} xong):",
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
                            text = "Thêm việc con",
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
                    title = { Text("Đăng ký dời hạn dự án", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Thời hạn hiện tại: ${project.safeEndDate.ifBlank { "Chưa xác định" }}",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                            OutlinedTextField(
                                value = extNewDate,
                                onValueChange = { extNewDate = it },
                                label = { Text("Hạn mới đề xuất (YYYY-MM-DD) *", fontSize = 11.sp) },
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
                                label = { Text("Lý do xin dời hạn *", fontSize = 11.sp) },
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
                            Text("Gửi yêu cầu", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showExtensionDialog = false }) {
                            Text("Hủy", color = TextMuted)
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
                                onCheckedChange = { onToggleChild(ct.id) },
                                colors = CheckboxDefaults.colors(checkedColor = PrimaryPurple)
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
                                                text = "Khẩn cấp",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFDC2626),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                if (ct.safeAssigneeName.isNotBlank() || ct.safeEndDate.isNotBlank()) {
                                    Text(
                                        text = "${ct.safeAssigneeName} • ${CalendarUtils.formatDisplayRange(ct.safeStartDate, ct.safeEndDate)}",
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
                    text = "Chưa có công việc con nào. Bấm 'Thêm việc con' ở trên để thêm hạng mục.",
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
    var selectedTaskChinhTitle by remember { mutableStateOf(taskChinhList.firstOrNull()?.title ?: "") }
    var expandedDropdownTaskChinh by remember { mutableStateOf(false) }

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
    var expandedDept by remember { mutableStateOf(false) }

    val phanLoaiTaskOptions = listOf("Phòng ban", "Công ty")
    var selectedPhanLoaiTask by remember { mutableStateOf("Phòng ban") }
    var expandedPhanLoaiTask by remember { mutableStateOf(false) }

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
    var expandedPhanLoaiViec by remember { mutableStateOf(false) }

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
                    text = if (isChildTaskMode) "THÊM CÔNG VIỆC CON" else "THÊM CÔNG VIỆC CHÍNH",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Đóng", tint = TextMuted)
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
                        text = "Công việc chính",
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
                        text = "Công việc con",
                        fontSize = 12.sp,
                        fontWeight = if (isChildTaskMode) FontWeight.Bold else FontWeight.Normal,
                        color = if (isChildTaskMode) PrimaryPurple else TextMuted
                    )
                }
            }

            if (isChildTaskMode) {
                // CHẾ ĐỘ THÊM CÔNG VIỆC CON (Chuẩn PowerApp: 4 trường + Chọn Task chính)
                Column {
                    Text(text = "Thuộc công việc chính nào *", fontSize = 12.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedCard(
                            onClick = { expandedDropdownTaskChinh = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (selectedTaskChinhTitle.isNotBlank()) selectedTaskChinhTitle else "Chọn công việc chính",
                                    fontSize = 13.sp,
                                    color = TextDark,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextMuted)
                            }
                        }

                        DropdownMenu(
                            expanded = expandedDropdownTaskChinh,
                            onDismissRequest = { expandedDropdownTaskChinh = false }
                        ) {
                            taskChinhList.forEach { tc ->
                                DropdownMenuItem(
                                    text = { Text(tc.safeTitle, fontSize = 13.sp) },
                                    onClick = {
                                        selectedTaskChinhId = tc.id
                                        selectedTaskChinhTitle = tc.safeTitle
                                        expandedDropdownTaskChinh = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Công việc *", fontSize = 12.sp) },
                    placeholder = { Text("Nhập công việc chi tiết", fontSize = 12.sp, color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    shape = RoundedCornerShape(8.dp)
                )

                SingleUserSelector(
                    label = "Người phụ trách *",
                    selectedUser = selectedChildAssignee,
                    users = users,
                    onSelect = { selectedChildAssignee = it }
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it },
                        label = { Text("Ngày bắt đầu", fontSize = 11.sp) },
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
                        label = { Text("Ngày kết thúc", fontSize = 11.sp) },
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
                    Text(text = "Thêm", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            } else {
                // CHẾ ĐỘ THÊM CÔNG VIỆC CHÍNH (Chuẩn PowerApp: 8 trường chuẩn xác)
                // Trường 1: Tên công việc *
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tên công việc *", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    shape = RoundedCornerShape(8.dp)
                )

                // Trường 2: Cấp giao việc * (Công ty, Phòng ban, Cá nhân)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedCard(
                        onClick = { expandedPhanLoaiTask = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "Cấp giao việc *", fontSize = 11.sp, color = TextMuted)
                                Text(text = selectedPhanLoaiTask, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextDark)
                            }
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextMuted)
                        }
                    }

                    DropdownMenu(
                        expanded = expandedPhanLoaiTask,
                        onDismissRequest = { expandedPhanLoaiTask = false }
                    ) {
                        phanLoaiTaskOptions.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt, fontSize = 13.sp) },
                                onClick = {
                                    selectedPhanLoaiTask = opt
                                    expandedPhanLoaiTask = false
                                }
                            )
                        }
                    }
                }

                // Trường 3: Phân loại công việc * (Kế hoạch, Định kỳ, Đột xuất, Dự án)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedCard(
                        onClick = { expandedPhanLoaiViec = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "Phân loại công việc *", fontSize = 11.sp, color = TextMuted)
                                Text(text = selectedPhanLoaiViec, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextDark)
                            }
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextMuted)
                        }
                    }

                    DropdownMenu(
                        expanded = expandedPhanLoaiViec,
                        onDismissRequest = { expandedPhanLoaiViec = false }
                    ) {
                        phanLoaiViecOptions.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt, fontSize = 13.sp) },
                                onClick = {
                                    selectedPhanLoaiViec = opt
                                    expandedPhanLoaiViec = false
                                }
                            )
                        }
                    }
                }

                // Trường 4: Người phụ trách *
                SingleUserSelector(
                    label = "Người phụ trách *",
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
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedCard(
                        onClick = { expandedDept = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "Phòng ban *", fontSize = 11.sp, color = TextMuted)
                                Text(text = selectedDept, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextDark)
                            }
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextMuted)
                        }
                    }

                    DropdownMenu(
                        expanded = expandedDept,
                        onDismissRequest = { expandedDept = false }
                    ) {
                        deptOptions.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt, fontSize = 13.sp) },
                                onClick = {
                                    selectedDept = opt
                                    expandedDept = false
                                }
                            )
                        }
                    }
                }

                // Trường 6: CC Email
                MultiUserSelector(
                    label = "CC Email",
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
                        label = { Text("Ngày bắt đầu", fontSize = 11.sp) },
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
                        label = { Text("Ngày kết thúc", fontSize = 11.sp) },
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
                    Text(text = "Thêm", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

/**
 * Màn hình đăng nhập Microsoft 365
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    repository: TaskRepository,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var email by remember { mutableStateOf(repository.getSavedEmail().ifBlank { "sang.nguyen@seazen.com.vn" }) }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    var tokenInput by remember { mutableStateOf(repository.getSavedToken()) }
    var showManualToken by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    var deviceUserCode by remember { mutableStateOf("") }
    var deviceVerificationUri by remember { mutableStateOf("") }
    var isWaitingDeviceLogin by remember { mutableStateOf(false) }

    Scaffold(containerColor = BackgroundLight) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorderColor)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(22.dp)) {
                    Text(text = "SZ TASK", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PrimaryPurple)
                    Text(
                        text = "Đăng nhập tài khoản Microsoft 365 để tự động lấy Token",
                        fontSize = 12.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Tài khoản Microsoft (Email)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = TextMuted) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Mật khẩu Microsoft") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = TextMuted) },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = TextMuted
                                )
                            }
                        }
                    )

                    if (isWaitingDeviceLogin && deviceUserCode.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFEFF6FF),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFBFDBFE)))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "Xác thực 2 lớp Microsoft (MFA)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(text = deviceUserCode, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = PrimaryPurple, letterSpacing = 2.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Mã xác thực đã được sao chép. Bấm nút bên dưới để mở trang đăng nhập Microsoft.",
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
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Mở trang xác thực Microsoft", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    if (errorMessage.isNotBlank() && !isWaitingDeviceLogin) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = errorMessage, fontSize = 12.sp, color = Color(0xFFDC2626), modifier = Modifier.padding(vertical = 4.dp))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            errorMessage = ""
                            if (email.isBlank() || password.isBlank()) {
                                errorMessage = "Vui lòng nhập đầy đủ Email và Mật khẩu."
                                return@Button
                            }
                            isLoading = true
                            isWaitingDeviceLogin = false

                            coroutineScope.launch {
                                val res = repository.loginMicrosoft(email, password)
                                isLoading = false
                                if (res.success) {
                                    Toast.makeText(context, "Đăng nhập Microsoft thành công", Toast.LENGTH_SHORT).show()
                                    onLoginSuccess()
                                } else {
                                    if (res.isMfaRequired) {
                                        val devRes = repository.authManager.requestDeviceCode()
                                        if (devRes != null) {
                                            deviceUserCode = devRes.userCode
                                            deviceVerificationUri = devRes.verificationUri
                                            isWaitingDeviceLogin = true
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("user_code", devRes.userCode))
                                            Toast.makeText(context, "Đã sao chép mã xác thực: ${devRes.userCode}", Toast.LENGTH_LONG).show()

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
                                                        errorMessage = "Mã xác thực đã hết hạn, vui lòng thử lại."
                                                    }
                                                }
                                            }
                                        } else {
                                            errorMessage = "Không thể tạo mã xác thực Device Code."
                                        }
                                    } else {
                                        errorMessage = res.errorMessage ?: "Đăng nhập thất bại"
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Đang kết nối Microsoft...")
                        } else {
                            Text("ĐĂNG NHẬP MICROSOFT", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        TextButton(onClick = { showManualToken = !showManualToken }) {
                            Text(
                                text = if (showManualToken) "Ẩn mục dán Token thủ công" else "Hoặc dán Token trực tiếp",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }

                    if (showManualToken) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            label = { Text("SharePoint Bearer Token") },
                            placeholder = { Text("Dán token...") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3,
                            shape = RoundedCornerShape(10.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = clipboard.primaryClip
                                    if (clip != null && clip.itemCount > 0) {
                                        val text = clip.getItemAt(0).text?.toString() ?: ""
                                        if (text.isNotBlank()) tokenInput = text.trim()
                                    }
                                }
                            ) {
                                Text("Dán từ bộ nhớ tạm", fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    if (tokenInput.isNotBlank()) {
                                        repository.saveEmail(email)
                                        repository.saveToken(tokenInput)
                                        coroutineScope.launch {
                                            repository.fetchAllFromSharePoint(tokenInput)
                                            onLoginSuccess()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark)
                            ) {
                                Text("Xác nhận Token", fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            repository.saveEmail(email.ifBlank { "sang.nguyen@seazen.com.vn" })
                            repository.loadCachedData()
                            onLoginSuccess()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Dùng thử dữ liệu mẫu (Offline)", color = TextDark)
                    }
                }
            }
        }
    }
}
