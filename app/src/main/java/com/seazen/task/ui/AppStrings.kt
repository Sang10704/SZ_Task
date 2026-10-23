package com.seazen.task.ui

import androidx.compose.runtime.compositionLocalOf

val LocalAppLanguage = compositionLocalOf { "vi" }

object AppStrings {

    fun of(lang: String): Dictionary = if (lang.equals("en", ignoreCase = true)) EnDictionary else ViDictionary

    interface Dictionary {
        // Navigation Tabs
        val navMyTasks: String
        val navDepartments: String
        val navCalendar: String
        val navSettings: String

        // Common Actions
        val actionClose: String
        val actionCancel: String
        val actionConfirm: String
        val actionSave: String
        val actionCreate: String
        val actionDelete: String
        val actionEdit: String
        val actionRefresh: String
        val actionSyncNow: String
        val actionAdd: String
        val actionLogout: String

        // My Tasks Tab
        val searchPlaceholder: String
        val greetingPrefix: String
        val greetingSub: String
        val statToday: String
        val statDone: String
        val statOverdue: String
        val sectionMyProjects: String
        val labelProgress: String
        val filterToday: String
        val filterUpcoming: String
        val filterOverdue: String
        val filterAll: String
        val emptyTodayTitle: String
        val emptyTodaySub: String
        val emptySearchTitle: String
        val emptySearchSub: String

        // Status & Priority Tags
        val tagUrgent: String
        val tagNormal: String
        val tagOverdue: String
        val tagToday: String
        val tagCompleted: String
        val tagInProgress: String
        val tagReadonly: String
        val tagDepartment: String
        val tagPersonal: String

        // Department Tab
        val deptTabTitle: String
        val deptTabSubtitle: String
        val filterAllDepts: String
        val labelProjects: String
        val labelSubtasks: String
        val labelLead: String
        val labelDue: String

        // Calendar Tab
        val calendarTabTitle: String
        val calendarTabSubtitle: String
        val calendarTasksForDay: String
        val calendarNoTasks: String
        val calendarAddTaskForDay: String
        val dayMon: String
        val dayTue: String
        val dayWed: String
        val dayThu: String
        val dayFri: String
        val daySat: String
        val daySun: String

        // Settings Tab
        val settingsTitle: String
        val sectionProfile: String
        val sectionLanguage: String
        val sectionSync: String
        val sectionDanger: String
        val langVietnamese: String
        val langEnglish: String
        val labelLastSync: String
        val labelAppVersion: String
        val actionResetData: String
        val logoutConfirmTitle: String
        val logoutConfirmMsg: String

        // Task Detail Sheet
        val taskDetailTitle: String
        val labelTaskTitle: String
        val labelParentProject: String
        val labelAssignee: String
        val labelPriority: String
        val labelStartDate: String
        val labelEndDate: String
        val labelNotes: String
        val actionMarkCompleted: String
        val actionMarkPending: String
        val actionViewParentProject: String
        val noticeReadonly: String

        // Project Detail Sheet
        val projectDetailTitle: String
        val labelProjectProgress: String
        val labelProjectCreator: String
        val labelProjectCc: String
        val labelSubtaskList: String
        val actionAddSubtask: String
        val actionExtendDeadline: String

        // Create Task Sheet
        val createTaskTitle: String
        val fieldTaskTitle: String
        val fieldParentProject: String
        val fieldAssignee: String
        val fieldPriority: String
        val fieldStartDate: String
        val fieldEndDate: String
        val fieldNotes: String
        val btnCreateTask: String

        // Login Screen
        val loginTitle: String
        val loginSubtitle: String
        val fieldEmail: String
        val fieldPassword: String
        val btnLogin: String
        val btnDemoMode: String
        val loginErrorBlank: String

        // Additional UI & Form keys
        val tabMainProject: String
        val tabSubTask: String
        val titleAddMainProject: String
        val titleAddSubtask: String
        val labelBelongsToProject: String
        val labelTimeRange: String
        val labelStatus: String
        val labelClassification: String
        val labelJobNature: String
        val labelProjectLead: String
        val labelAssignedBy: String
        val labelCc: String
        val labelNewDeadline: String
        val labelExtensionRequest: String
        val labelReason: String
        val labelNoReason: String
        val statusPendingApproval: String
        val statusInProgress: String
        val emptyFilterProjects: String
        val emptyCategoryTasks: String
        val labelScheduleForDay: String
        val sectionDayProjects: String
        val sectionDaySubtasks: String
        val sectionProjectSubtasks: String
        val labelDoneSuffix: String
        val searchDeptPlaceholder: String
        val defaultUserName: String

        // Crash Recovery
        val crashRecoveryTitle: String
        val crashRecoveryDesc: String
        val actionRestoreRestart: String
        val actionCopyCrashLog: String

        // Form helpers
        val placeholderSelectParentProject: String
        val labelBelongsToMainTask: String
        val placeholderDetailTask: String

        // Landing & Login
        val landingTitle: String
        val landingSubtitle: String
        val btnGetStarted: String
        val loginScreenTitle: String
        val orSignInWith: String
        val btnSignInWithMicrosoft: String
        val noSubtasks: String

        // Login & MFA
        val mfaTitle: String
        val mfaDesc: String
        val btnOpenMfa: String
        val btnConnectingMicrosoft: String
        val btnSignInMicrosoft: String
        val toggleManualTokenHide: String
        val toggleManualTokenShow: String
        val labelBearerToken: String
        val placeholderPasteToken: String
        val btnPasteClipboard: String
        val btnConfirmToken: String
        val btnDemoOffline: String

        // Scope & Department Filter
        val filterScopePersonal: String
        val filterScopeAll: String
        val filterScopeTitle: String
        val filterScopeSubtitle: String
        val searchCalendarPlaceholder: String
        val searchDepartmentPlaceholder: String
        val labelFilterBy: String
        val labelScopePersonal: String
        val labelScopeAll: String
    }

    private object ViDictionary : Dictionary {
        override val navMyTasks = "Cá nhân"
        override val navDepartments = "Phòng ban"
        override val navCalendar = "Lịch biểu"
        override val navSettings = "Cài đặt"

        override val actionClose = "Đóng"
        override val actionCancel = "Hủy"
        override val actionConfirm = "Xác nhận"
        override val actionSave = "Lưu thay đổi"
        override val actionCreate = "Tạo mới"
        override val actionDelete = "Xóa"
        override val actionEdit = "Chỉnh sửa"
        override val actionRefresh = "Làm mới"
        override val actionSyncNow = "Đồng bộ SharePoint ngay"
        override val actionAdd = "Thêm mới"
        override val actionLogout = "Đăng xuất tài khoản"

        override val searchPlaceholder = "Tìm việc, dự án..."
        override val greetingPrefix = "Xin chào,"
        override val greetingSub = "Chào mừng trở lại SZ Task"
        override val statToday = "Hôm nay"
        override val statDone = "Đã xong"
        override val statOverdue = "Trễ hạn"
        override val sectionMyProjects = "Dự án phụ trách"
        override val labelProgress = "Tiến độ"
        override val filterToday = "Hôm nay"
        override val filterUpcoming = "Sắp tới"
        override val filterOverdue = "Trễ hạn"
        override val filterAll = "Tất cả"
        override val emptyTodayTitle = "Tuyệt vời! Không còn việc tồn"
        override val emptyTodaySub = "Bạn đã hoàn thành tất cả công việc cho ngày này."
        override val emptySearchTitle = "Không tìm thấy công việc"
        override val emptySearchSub = "Thử tìm kiếm với từ khóa khác hoặc chuyển bộ lọc."

        override val tagUrgent = "Khẩn cấp"
        override val tagNormal = "Bình thường"
        override val tagOverdue = "Trễ hạn"
        override val tagToday = "Hôm nay"
        override val tagCompleted = "Hoàn thành"
        override val tagInProgress = "Đang làm"
        override val tagReadonly = "Chỉ xem"
        override val tagDepartment = "Phòng ban"
        override val tagPersonal = "Cá nhân"

        override val deptTabTitle = "Phòng ban & Công ty"
        override val deptTabSubtitle = "Theo dõi mục tiêu và nhiệm vụ toàn đơn vị"
        override val filterAllDepts = "Tất cả"
        override val labelProjects = "dự án"
        override val labelSubtasks = "việc"
        override val labelLead = "Phụ trách:"
        override val labelDue = "Hạn:"

        override val calendarTabTitle = "Lịch biểu công tác"
        override val calendarTabSubtitle = "Kế hoạch tuần & tháng"
        override val calendarTasksForDay = "Nhiệm vụ trong ngày"
        override val calendarNoTasks = "Không có công việc nào trong ngày này"
        override val calendarAddTaskForDay = "Thêm việc cho ngày này"
        override val dayMon = "T2"
        override val dayTue = "T3"
        override val dayWed = "T4"
        override val dayThu = "T5"
        override val dayFri = "T6"
        override val daySat = "T7"
        override val daySun = "CN"

        override val settingsTitle = "Cài đặt hệ thống"
        override val sectionProfile = "Hồ sơ người dùng"
        override val sectionLanguage = "Ngôn ngữ hiển thị"
        override val sectionSync = "Dữ liệu & Đồng bộ"
        override val sectionDanger = "Khu vực quản trị"
        override val langVietnamese = "Tiếng Việt (Mặc định)"
        override val langEnglish = "English (Tiếng Anh)"
        override val labelLastSync = "Lần đồng bộ gần nhất:"
        override val labelAppVersion = "Phiên bản ứng dụng:"
        override val actionResetData = "Khôi phục dữ liệu mẫu ban đầu"
        override val logoutConfirmTitle = "Xác nhận đăng xuất"
        override val logoutConfirmMsg = "Bạn có chắc chắn muốn đăng xuất khỏi tài khoản SeaZen Portal?"

        override val taskDetailTitle = "Chi tiết công việc"
        override val labelTaskTitle = "Tên công việc"
        override val labelParentProject = "Dự án liên kết"
        override val labelAssignee = "Người thực hiện"
        override val labelPriority = "Độ ưu tiên"
        override val labelStartDate = "Ngày bắt đầu"
        override val labelEndDate = "Hạn hoàn thành"
        override val labelNotes = "Ghi chú & Chi tiết"
        override val actionMarkCompleted = "Đánh dấu hoàn thành"
        override val actionMarkPending = "Đánh dấu chưa xong"
        override val actionViewParentProject = "Xem dự án cha"
        override val noticeReadonly = "Bạn chỉ có quyền xem công việc này (Không thể sửa đổi/xóa)."

        override val projectDetailTitle = "Chi tiết dự án"
        override val labelProjectProgress = "Tiến độ thực hiện"
        override val labelProjectCreator = "Người giao việc"
        override val labelProjectCc = "Người nhận thông báo"
        override val labelSubtaskList = "Danh sách việc con"
        override val actionAddSubtask = "Thêm việc con"
        override val actionExtendDeadline = "Đề xuất đổi hạn"

        override val createTaskTitle = "Tạo công việc mới"
        override val fieldTaskTitle = "Tiêu đề công việc (*)"
        override val fieldParentProject = "Thuộc dự án (*)"
        override val fieldAssignee = "Người thực hiện (*)"
        override val fieldPriority = "Độ ưu tiên"
        override val fieldStartDate = "Ngày bắt đầu"
        override val fieldEndDate = "Hạn hoàn thành"
        override val fieldNotes = "Ghi chú công việc"
        override val btnCreateTask = "Tạo công việc"

        override val loginTitle = "Đăng nhập SeaZen Portal"
        override val loginSubtitle = "Sử dụng tài khoản công ty @seazen.com.vn"
        override val fieldEmail = "Email tài khoản"
        override val fieldPassword = "Mật khẩu"
        override val btnLogin = "Đăng nhập"
        override val btnDemoMode = "Dùng thử chế độ Offline"
        override val loginErrorBlank = "Vui lòng nhập đầy đủ email và mật khẩu."

        // Additional UI & Form keys
        override val tabMainProject = "Công việc chính"
        override val tabSubTask = "Công việc con"
        override val titleAddMainProject = "THÊM CÔNG VIỆC CHÍNH"
        override val titleAddSubtask = "THÊM CÔNG VIỆC CON"
        override val labelBelongsToProject = "Thuộc dự án / mục tiêu chính:"
        override val labelTimeRange = "Thời hạn thực hiện"
        override val labelStatus = "Trạng thái"
        override val labelClassification = "Cấp độ phân loại"
        override val labelJobNature = "Tính chất công việc"
        override val labelProjectLead = "Chủ trì dự án"
        override val labelAssignedBy = "Người giao việc"
        override val labelCc = "Người nhận TB (CC)"
        override val labelNewDeadline = "Gia hạn mới"
        override val labelExtensionRequest = "Yêu cầu dời hạn:"
        override val labelReason = "Lý do:"
        override val labelNoReason = "Không có lý do"
        override val statusPendingApproval = "Chờ duyệt"
        override val statusInProgress = "Đang thực hiện"
        override val emptyFilterProjects = "Không tìm thấy công việc phù hợp với bộ lọc"
        override val emptyCategoryTasks = "Không có công việc cá nhân trong danh mục này"
        override val labelScheduleForDay = "Lịch ngày"
        override val sectionDayProjects = "Dự án & Mục tiêu chính"
        override val sectionDaySubtasks = "Công việc chi tiết"
        override val sectionProjectSubtasks = "Hạng mục chi tiết"
        override val labelDoneSuffix = "xong"
        override val searchDeptPlaceholder = "Tìm kiếm dự án, phòng ban, người chủ trì..."
        override val defaultUserName = "Người dùng SeaZen"

        // Crash Recovery
        override val crashRecoveryTitle = "Khôi phục trạng thái SZ Task"
        override val crashRecoveryDesc = "Hệ thống tự động phát hiện và ngăn ngừa lỗi dừng đột ngột:"
        override val actionRestoreRestart = "Khôi phục bộ nhớ & Khởi động lại"
        override val actionCopyCrashLog = "Sao chép chi tiết lỗi"

        // Form helpers
        override val placeholderSelectParentProject = "Chọn công việc chính"
        override val labelBelongsToMainTask = "Thuộc công việc chính nào *"
        override val placeholderDetailTask = "Nhập công việc chi tiết"

        // Landing & Login
        override val landingTitle = "SZ TASK"
        override val landingSubtitle = "Hệ thống quản lý công việc & dự án SeaZen"
        override val btnGetStarted = "Bắt đầu ngay"
        override val loginScreenTitle = "Đăng nhập tài khoản"
        override val orSignInWith = "Hoặc đăng nhập bằng"
        override val btnSignInWithMicrosoft = "Tài khoản Microsoft 365"
        override val noSubtasks = "Chưa có công việc chi tiết nào"

        // Login & MFA
        override val mfaTitle = "Xác thực 2 lớp Microsoft (MFA)"
        override val mfaDesc = "Mã xác thực đã được sao chép. Bấm nút bên dưới để mở trang đăng nhập Microsoft."
        override val btnOpenMfa = "Mở trang xác thực Microsoft"
        override val btnConnectingMicrosoft = "Đang kết nối Microsoft..."
        override val btnSignInMicrosoft = "ĐĂNG NHẬP MICROSOFT"
        override val toggleManualTokenHide = "Ẩn mục dán Token thủ công"
        override val toggleManualTokenShow = "Hoặc dán Token trực tiếp"
        override val labelBearerToken = "SharePoint Bearer Token"
        override val placeholderPasteToken = "Dán token..."
        override val btnPasteClipboard = "Dán từ bộ nhớ tạm"
        override val btnConfirmToken = "Xác nhận Token"
        override val btnDemoOffline = "Dùng thử dữ liệu mẫu (Offline)"

        // Scope & Department Filter
        override val filterScopePersonal = "Cá nhân (Mặc định)"
        override val filterScopeAll = "Tất cả phòng ban"
        override val filterScopeTitle = "Bộ lọc hiển thị"
        override val filterScopeSubtitle = "Chọn phạm vi công việc hoặc phòng ban muốn theo dõi"
        override val searchCalendarPlaceholder = "Tìm việc, dự án trong lịch..."
        override val searchDepartmentPlaceholder = "Tìm kiếm phòng ban..."
        override val labelFilterBy = "Lọc theo:"
        override val labelScopePersonal = "Cá nhân"
        override val labelScopeAll = "Tất cả"
    }

    private object EnDictionary : Dictionary {
        override val navMyTasks = "My Tasks"
        override val navDepartments = "Departments"
        override val navCalendar = "Calendar"
        override val navSettings = "Settings"

        override val actionClose = "Close"
        override val actionCancel = "Cancel"
        override val actionConfirm = "Confirm"
        override val actionSave = "Save Changes"
        override val actionCreate = "Create"
        override val actionDelete = "Delete"
        override val actionEdit = "Edit"
        override val actionRefresh = "Refresh"
        override val actionSyncNow = "Sync SharePoint Now"
        override val actionAdd = "Add New"
        override val actionLogout = "Log Out"

        override val searchPlaceholder = "Search tasks, projects..."
        override val greetingPrefix = "Hello,"
        override val greetingSub = "Welcome back to SZ Task"
        override val statToday = "Today"
        override val statDone = "Completed"
        override val statOverdue = "Overdue"
        override val sectionMyProjects = "Assigned Projects"
        override val labelProgress = "Progress"
        override val filterToday = "Today"
        override val filterUpcoming = "Upcoming"
        override val filterOverdue = "Overdue"
        override val filterAll = "All"
        override val emptyTodayTitle = "All Clear! No pending tasks"
        override val emptyTodaySub = "You have completed all scheduled tasks for this day."
        override val emptySearchTitle = "No tasks found"
        override val emptySearchSub = "Try searching with different keywords or switch filters."

        override val tagUrgent = "Urgent"
        override val tagNormal = "Normal"
        override val tagOverdue = "Overdue"
        override val tagToday = "Today"
        override val tagCompleted = "Completed"
        override val tagInProgress = "In Progress"
        override val tagReadonly = "Read-only"
        override val tagDepartment = "Department"
        override val tagPersonal = "Personal"

        override val deptTabTitle = "Departments & Company"
        override val deptTabSubtitle = "Monitor organizational objectives and tasks"
        override val filterAllDepts = "All"
        override val labelProjects = "projects"
        override val labelSubtasks = "tasks"
        override val labelLead = "Lead:"
        override val labelDue = "Due:"

        override val calendarTabTitle = "Work Calendar"
        override val calendarTabSubtitle = "Weekly & monthly work schedule"
        override val calendarTasksForDay = "Tasks for selected day"
        override val calendarNoTasks = "No tasks scheduled for this day"
        override val calendarAddTaskForDay = "Add task for this day"
        override val dayMon = "Mon"
        override val dayTue = "Tue"
        override val dayWed = "Wed"
        override val dayThu = "Thu"
        override val dayFri = "Fri"
        override val daySat = "Sat"
        override val daySun = "Sun"

        override val settingsTitle = "System Settings"
        override val sectionProfile = "User Profile"
        override val sectionLanguage = "Display Language"
        override val sectionSync = "Data & Sync"
        override val sectionDanger = "Administration"
        override val langVietnamese = "Tiếng Việt (Vietnamese)"
        override val langEnglish = "English (Default)"
        override val labelLastSync = "Last synced:"
        override val labelAppVersion = "App Version:"
        override val actionResetData = "Reset to default sample data"
        override val logoutConfirmTitle = "Confirm Logout"
        override val logoutConfirmMsg = "Are you sure you want to log out of your SeaZen account?"

        override val taskDetailTitle = "Task Details"
        override val labelTaskTitle = "Task Title"
        override val labelParentProject = "Parent Project"
        override val labelAssignee = "Assignee"
        override val labelPriority = "Priority"
        override val labelStartDate = "Start Date"
        override val labelEndDate = "Due Date"
        override val labelNotes = "Notes & Details"
        override val actionMarkCompleted = "Mark as Completed"
        override val actionMarkPending = "Mark as In Progress"
        override val actionViewParentProject = "View Parent Project"
        override val noticeReadonly = "You have read-only access to this task (Cannot edit/delete)."

        override val projectDetailTitle = "Project Details"
        override val labelProjectProgress = "Overall Progress"
        override val labelProjectCreator = "Assigned By"
        override val labelProjectCc = "CC Notified"
        override val labelSubtaskList = "Subtasks List"
        override val actionAddSubtask = "Add Subtask"
        override val actionExtendDeadline = "Request Extension"

        override val createTaskTitle = "Create New Task"
        override val fieldTaskTitle = "Task Title (*)"
        override val fieldParentProject = "Select Project (*)"
        override val fieldAssignee = "Assignee (*)"
        override val fieldPriority = "Priority"
        override val fieldStartDate = "Start Date"
        override val fieldEndDate = "Due Date"
        override val fieldNotes = "Task Notes"
        override val btnCreateTask = "Create Task"

        override val loginTitle = "Sign in to SeaZen Portal"
        override val loginSubtitle = "Use your company account @seazen.com.vn"
        override val fieldEmail = "Account Email"
        override val fieldPassword = "Password"
        override val btnLogin = "Sign In"
        override val btnDemoMode = "Try Offline Demo"
        override val loginErrorBlank = "Please enter both email and password."

        // Additional UI & Form keys
        override val tabMainProject = "Main Project"
        override val tabSubTask = "Subtask"
        override val titleAddMainProject = "ADD MAIN PROJECT"
        override val titleAddSubtask = "ADD SUBTASK"
        override val labelBelongsToProject = "Belongs to Project / Goal:"
        override val labelTimeRange = "Schedule Duration"
        override val labelStatus = "Status"
        override val labelClassification = "Classification"
        override val labelJobNature = "Work Category"
        override val labelProjectLead = "Project Lead"
        override val labelAssignedBy = "Assigned By"
        override val labelCc = "CC Notified"
        override val labelNewDeadline = "New Due Date"
        override val labelExtensionRequest = "Extension Request:"
        override val labelReason = "Reason:"
        override val labelNoReason = "No reason provided"
        override val statusPendingApproval = "Pending Approval"
        override val statusInProgress = "In Progress"
        override val emptyFilterProjects = "No projects matching selected filter"
        override val emptyCategoryTasks = "No personal tasks in this category"
        override val labelScheduleForDay = "Schedule for"
        override val sectionDayProjects = "Projects & Main Goals"
        override val sectionDaySubtasks = "Detailed Tasks"
        override val sectionProjectSubtasks = "Subtask Breakdown"
        override val labelDoneSuffix = "completed"
        override val searchDeptPlaceholder = "Search projects, departments, leads..."
        override val defaultUserName = "SeaZen User"

        // Crash Recovery
        override val crashRecoveryTitle = "SZ Task Recovery"
        override val crashRecoveryDesc = "System detected an unhandled issue and protected application data:"
        override val actionRestoreRestart = "Restore Defaults & Restart"
        override val actionCopyCrashLog = "Copy Error Details"

        // Form helpers
        override val placeholderSelectParentProject = "Select main project"
        override val labelBelongsToMainTask = "Belongs to main project *"
        override val placeholderDetailTask = "Enter detailed task description"

        // Landing & Login
        override val landingTitle = "SZ TASK"
        override val landingSubtitle = "SeaZen Task & Project Management"
        override val btnGetStarted = "Get Started"
        override val loginScreenTitle = "Login to your Account"
        override val orSignInWith = "Or sign in with"
        override val btnSignInWithMicrosoft = "Microsoft 365 Account"
        override val noSubtasks = "No subtasks available"

        // Login & MFA
        override val mfaTitle = "Microsoft Multi-Factor Authentication (MFA)"
        override val mfaDesc = "Verification code copied. Tap the button below to open Microsoft sign-in page."
        override val btnOpenMfa = "Open Microsoft Verification Page"
        override val btnConnectingMicrosoft = "Connecting to Microsoft..."
        override val btnSignInMicrosoft = "SIGN IN WITH MICROSOFT"
        override val toggleManualTokenHide = "Hide manual Token input"
        override val toggleManualTokenShow = "Or paste Token directly"
        override val labelBearerToken = "SharePoint Bearer Token"
        override val placeholderPasteToken = "Paste token..."
        override val btnPasteClipboard = "Paste from clipboard"
        override val btnConfirmToken = "Confirm Token"
        override val btnDemoOffline = "Try sample data (Offline)"

        // Scope & Department Filter
        override val filterScopePersonal = "Personal (Default)"
        override val filterScopeAll = "All Departments"
        override val filterScopeTitle = "Display Filter"
        override val filterScopeSubtitle = "Select work scope or department to monitor"
        override val searchCalendarPlaceholder = "Search calendar tasks & projects..."
        override val searchDepartmentPlaceholder = "Search department..."
        override val labelFilterBy = "Filter by:"
        override val labelScopePersonal = "Personal"
        override val labelScopeAll = "All"
    }
}
