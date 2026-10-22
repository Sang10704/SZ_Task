# SZ Task - SeaZen Task Management App

Ứng dụng Android quản lý công việc hàng ngày và theo dõi tiến độ dự án phòng ban tại SeaZen, tích hợp xác thực Microsoft Azure AD và đồng bộ dữ liệu SharePoint Online.

## Tính năng chính

- **Cá nhân (My Tasks):** Theo dõi công việc trong ngày, phân loại mức độ khẩn cấp, kiểm soát công việc quá hạn và lọc theo trạng thái hoàn thành.
- **Phòng ban & Dự án:** Quản lý các dự án lớn (`TaskChinh`), tự động tính toán tỷ lệ phần trăm tiến độ dựa trên các công việc con liên kết.
- **Lịch biểu (Calendar):** Chế độ xem lịch tháng, tuần và chọn ngày để kiểm tra lịch công tác, nhiệm vụ cụ thể.
- **Hệ thống Widget Android:**
  - *SZ Task: Việc Hôm Nay:* Danh sách công việc dạng cuộn, hỗ trợ tick hoàn thành trực tiếp trên màn hình chính và nút tạo việc nhanh.
  - *SZ Task: Thống Kê Tổng Quan:* Thẻ số liệu trực quan (Hôm nay, Đã xong, Trễ hạn) và phím tắt điều hướng nhanh.
  - *SZ Task: Tiến Độ Dự Án:* Thanh tiến độ mục tiêu phòng ban theo thời gian thực.

## Công nghệ sử dụng

- **Ngôn ngữ:** Kotlin (JVM 17)
- **Giao diện:** Jetpack Compose, Material Design 3
- **Kiến trúc:** MVVM, Coroutines & Kotlin StateFlow
- **Mạng & Dữ liệu:** OkHttp 4, Gson, Microsoft Authentication Library (MSAL) OAuth2 Bearer Token
- **Hệ điều hành hỗ trợ:** Android 8.0 (API level 26) trở lên

## Hướng dẫn cài đặt & Biên dịch

```bash
# Biên dịch gói gỡ lỗi (Debug APK)
./gradlew assembleDebug

# Cài đặt trực tiếp lên thiết bị Android qua ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
