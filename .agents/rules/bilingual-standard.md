# Quy chuẩn Đồng bộ Ngôn ngữ Anh - Việt (Bilingual Standard)

Quy định bắt buộc áp dụng trên toàn bộ mã nguồn ứng dụng SeaZen Task App:

## 1. Nguyên tắc cốt lõi
- **Không hardcode chuỗi hiển thị đơn ngữ:** Mọi văn bản hiển thị trên giao diện người dùng (tiêu đề, nhãn, nút bấm, placeholder, thông báo lỗi, trạng thái công việc, modal và widget) đều phải hỗ trợ đồng thời hai ngôn ngữ: Tiếng Việt (`vi`) và Tiếng Anh (`en`).
- **Nguồn chuỗi tập trung:** Tất cả chuỗi văn bản giao diện phải được quản lý tập trung thông qua đối tượng `AppStrings` (hoặc `LocalAppLanguage`). Tuyệt đối không viết chuỗi văn bản tĩnh tiếng Việt trực tiếp vào Composable hoặc layout mà không qua kiểm tra `isVi` hoặc `AppStrings.of(lang)`.
- **Đồng bộ toàn diện (Global Synchronization):** Khi người dùng thay đổi ngôn ngữ trong Cài đặt (`appLang = "en"` hoặc `"vi"`), toàn bộ 4 tab (`MyTasksTab`, `DepartmentTab`, `CalendarTab`, `SettingsTab`), thanh điều hướng `FloatingAppNavBar`, các BottomSheet (`TaskDetailBottomSheet`, `ProjectDetailBottomSheet`, `CreateTaskBottomSheet`), thông báo lỗi và hộp thoại xác nhận đều phải tự động chuyển đổi tương ứng ngay lập tức.

## 2. Chuẩn hóa từ điển thuật ngữ SeaZen (Terminology Mapping)

| Ngữ cảnh | Tiếng Việt (`vi`) | Tiếng Anh (`en`) |
| :--- | :--- | :--- |
| **Tab điều hướng** | Cá nhân, Phòng ban, Lịch biểu, Cài đặt | My Tasks, Departments, Calendar, Settings |
| **Chỉ số công việc** | Hôm nay, Đã xong, Trễ hạn, Khẩn cấp | Today, Completed, Overdue, Urgent |
| **Bộ lọc tab Cá nhân** | Hôm nay, Sắp tới, Trễ hạn, Tất cả | Today, Upcoming, Overdue, All |
| **Trạng thái tiến độ** | Đang làm, Hoàn thành, Trễ hạn | In Progress, Completed, Overdue |
| **Phân quyền / Thao tác** | Chỉ xem, Đánh dấu hoàn thành, Đổi hạn | Read-only, Mark as Completed, Extend Deadline |
| **Hành động cơ bản** | Thêm mới, Chỉnh sửa, Xóa, Lưu, Đóng, Hủy | Add, Edit, Delete, Save, Close, Cancel |
| **Xác thực & Tài khoản** | Đăng nhập, Đăng xuất, Người thực hiện, Người phụ trách | Sign In, Log Out, Assignee, Lead / Responsible |

## 3. Quy chuẩn khi viết tính năng / Composable mới
1. Mọi Composable nhận `lang: String` hoặc truy xuất `LocalAppLanguage.current`.
2. Khai báo chuỗi song ngữ trong `AppStrings`:
   ```kotlin
   val title = if (isVi) "Tiêu đề tiếng Việt" else "English Title"
   // hoặc
   val str = AppStrings.of(lang)
   Text(text = str.actionSave)
   ```
3. Đối với ngày tháng (`CalendarUtils`), phải truyền tham số ngôn ngữ để định dạng thứ và tháng chuẩn theo chuẩn quốc tế (`vi` vs `en`).
4. Khi phát sinh chuỗi mới, lập tức bổ sung cả 2 phiên bản `vi` và `en` trong cùng 1 lượt chỉnh sửa.
