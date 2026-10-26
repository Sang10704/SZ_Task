# Modern Soft UI & Mobile Card Design Standard

Quy chuẩn thiết kế giao diện Soft UI, Mobile Cards và Hệ thống Component áp dụng cho toàn bộ ứng dụng di động và dashboard trong hệ sinh thái SeaZen.

## 1. Bảng màu Chuẩn (SeaZen Soft Palette)

- **Màu nền ứng dụng (App Background)**:
  - Sáng mềm (Soft Slate/Lavender): `#F4F6FB` (Compose: `Color(0xFFF4F6FB)`).
  - Tránh dùng nền trắng tuyệt đối `#FFFFFF` cho toàn bộ màn hình; nền `#F4F6FB` làm nổi bật các Card trắng bo tròn.
- **Bề mặt & Thẻ (Surfaces & Cards)**:
  - Card chính: `#FFFFFF` (Compose: `Color.White`).
  - Đường viền thẻ (Card Border): Siêu mảnh `0.8.dp` với mã `#F1F5F9` hoặc `#E2E8F0`.
  - Sub-card / Vùng nhập liệu (Inputs): `#F8F9FD` hoặc `#F5F6FA`.
  - Featured Card / Banner nổi bật: Gradient tím sâu `Brush.horizontalGradient(listOf(Color(0xFF5B4DF6), Color(0xFF4338CA)))`.
- **Màu nhấn chính (Primary Accent)**:
  - SeaZen Purple: `#5B4DF6` (Compose: `Color(0xFF5B4DF6)`).
  - Tím đậm hỗ trợ: `#4338CA` (Compose: `Color(0xFF4338CA)`).
  - Tím pastel (Pill/Badge nền nhạt): `#EEF2FF` (Compose: `Color(0xFFEEF2FF)`).
- **Hệ thống chữ (Typography Hierarchy)**:
  - Tiêu đề chính (Headings): `#0F172A` hoặc `#18181B`, `FontWeight.Bold`.
  - Nội dung phụ / Label / Placeholder: `#64748B` hoặc `#94A3B8`, `FontWeight.Normal`.
  - Chữ trên nền tím / nút chính: `#FFFFFF`, `FontWeight.Bold` hoặc `SemiBold`.

## 2. Quy chuẩn Bo góc (Corner Radius Hierarchy)

Tuyệt đối không dùng góc nhọn hoặc bo góc nhỏ (`4.dp`, `6.dp`). Bắt buộc áp dụng hệ số chuẩn:
- **Card lớn / Bottom Sheet / Container ngoài**: `32.dp` hoặc `28.dp` (`RoundedCornerShape(32.dp)`).
- **Card phụ / Task Item / Section Card**: `20.dp` hoặc `16.dp` (`RoundedCornerShape(20.dp)`).
- **Nút hành động chính (CTA) & Chips lọc**: Dạng viên thuốc hoàn toàn (`CircleShape` hoặc `RoundedCornerShape(32.dp)`).
- **Ô nhập liệu (Form Inputs)**: `16.dp` (`RoundedCornerShape(16.dp)`).

## 3. Đổ bóng & Nổi khối (Diffuse Elevation & Shadows)

Không sử dụng các lớp shadow đen đậm gắt. Sử dụng kỹ thuật đổ bóng tán xạ mờ (Diffuse Ambient Glow):
- **Card trắng thông thường**:
  `elevation = 4.dp` hoặc `6.dp`, viền CardBorder siêu mảnh `#F1F5F9`.
- **Nút nổi bật / Floating Action Bar**:
  Đổ bóng `6.dp` đến `8.dp` với nền màu thương hiệu hoặc trắng tinh khôi bo tròn viên thuốc.

## 4. Cấu trúc Component Mẫu (Jetpack Compose)

### A. Nút bấm Hành động Chính (Pill CTA Button)
Chiều cao tối thiểu `50.dp` đến `54.dp`, bo tròn viên thuốc `RoundedCornerShape(32.dp)`, nền tím hoặc gradient tím:
```kotlin
Button(
    onClick = onClick,
    modifier = Modifier
        .fillMaxWidth()
        .height(52.dp),
    shape = RoundedCornerShape(32.dp),
    colors = ButtonDefaults.buttonColors(
        containerColor = Color(0xFF5B4DF6),
        contentColor = Color.White
    ),
    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
) {
    Text(
        text = label,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold
    )
}
```

### B. Ô Nhập liệu (Soft Input Field)
Nền phẳng xám nhạt `#F8F9FD`, không viền gắt, bo góc `16.dp`:
```kotlin
OutlinedTextField(
    value = text,
    onValueChange = onValueChange,
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color(0xFFF8F9FD),
        unfocusedContainerColor = Color(0xFFF8F9FD),
        focusedBorderColor = Color(0xFF5B4DF6),
        unfocusedBorderColor = Color(0xFFE2E8F0),
        cursorColor = Color(0xFF5B4DF6)
    ),
    singleLine = true
)
```

### C. Cơ chế Bắt buộc Cập nhật (Force Update with Session Preservation)
Khi có bản cập nhật mới trên hệ thống (`hasUpdate == true`):
1. **Khóa màn hình tại Landing / Update Screen**:
   - Không cho phép người dùng vượt qua để vào các tab làm việc chính khi chưa cài bản mới nhất.
   - Hiển thị rõ số hiệu phiên bản hiện tại so với phiên bản máy chủ (`v0.9.0` -> `v0.9.1`).
   - Nút hành động trực diện: "Cập nhật ứng dụng ngay" (tải tiến trình % trực tiếp và kích hoạt cài đặt OTA).
2. **Bảo toàn phiên đăng nhập (Preserve User Session)**:
   - TUYỆT ĐỐI KHÔNG đăng xuất (`logout()`) tài khoản người dùng khi hiển thị trang cập nhật.
   - Giữ nguyên thông tin xác thực (token, email, SharedPreferences). Sau khi người dùng cài đặt xong bản mới và mở lại app, hệ thống tự động nhận diện phiên đăng nhập và đưa thẳng vào màn hình làm việc mà không bắt đăng nhập lại.
