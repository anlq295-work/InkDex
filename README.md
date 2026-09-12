# InkDex Manga Reader 📖⚡

[![Release](https://img.shields.io/badge/Release-v1.0-black?style=flat-square)](https://github.com)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-black?style=flat-square)](https://developer.android.com)
[![E--Ink](https://img.shields.io/badge/E--Ink-Kaleido%203%20%7C%20Carta%201200-black?style=flat-square)](https://github.com)
[![License](https://img.shields.io/badge/License-MIT-black?style=flat-square)](LICENSE)

> **InkDex Manga Reader** là ứng dụng đọc truyện tranh trực tuyến từ **MangaDex** và ngoại tuyến qua file **.CBZ**, được thiết kế và tối ưu hóa chuyên biệt cho **Máy đọc sách E-Ink** (đặc biệt là màn hình màu **Kaleido 3** trên **Bigme B751C S / B751C** và các dòng máy đen trắng Onyx Boox, Meebook, Kindle Android).

---

## 🌟 Tính Năng Nổi Bật

### 1. 🎨 Tối ưu hóa Chuyên sâu cho Bigme B751C S & Kaleido 3
* **Bộ lọc màu riêng biệt `Kaleido 3` (Sống động)**: 
  * Tăng độ bão hòa màu **1.75x** giúp tranh màu và bìa truyện rực rỡ, khắc phục nhược điểm giới hạn 4.096 màu (12-bit) của tấm nền Kaleido 3.
  * Tăng tương phản kèm **hệ số nâng sáng dương (+20f)** bù trừ lượng ánh sáng bị lớp kính lọc màu CFA hấp thụ, giúp nền giấy trắng sáng và nét mực đen đậm, không bị bết màu.
* **Tự động nhận diện thiết bị**: Tự động nhận diện phần cứng máy đọc sách Bigme và ưu tiên kích hoạt sẵn chế độ màu phù hợp.
* **Gửi tín hiệu Refresh EPD phần cứng**: Gọi lệnh broadcast riêng của hệ điều hành Bigme (`com.bigme.action.REFRESH_SCREEN`) để kích hoạt chip xRapid làm sạch triệt để hiện tượng lưu ảnh (ghosting).

### 2. ⚡ Chống Lưu Ảnh & Tối Ưu Cho Màn Hình E-Ink
* **Tắt hiệu ứng co giãn trượt (Disable Overscroll)**: Loại bỏ độ nảy/đàn hồi khi cuộn mép trang, chống làm nhòe và bóng mờ màn hình.
* **Nút nhảy trang nổi [▲] [▼]**: Nhảy dứt khoát 1 trang màn hình trong các danh sách thay vì vuốt trượt ngón tay liên tục.
* **Cuộn nhảy khung hình trong truyện (Paged Reader Jump)**: Khi đọc truyện chế độ cuộn dọc hoặc tranh dài, chạm hoặc bấm phím âm lượng sẽ nhảy dứt khoát theo khung hình.
* **Khử bóng ma định kỳ**: Tự động làm mới toàn màn hình sau mỗi 1, 5, 10 hoặc 15 trang đọc.
* **Nút Khử bóng ma tức thì (Refresh E-Ink)**: Làm mới màn hình ngay lập tức chỉ với 1 chạm.

### 3. ⏭️ Trình Đọc Hiện Đại & Tự Động Chuyển Chương
* **Tự động chuyển chương kế (Auto Next Chapter)**: Khi lật đến trang cuối cùng của chương, chạm tiếp trang hoặc bấm phím âm lượng sẽ tự động tải chương tiếp theo liền mạch (hỗ trợ cả online MangaDex và offline CBZ).
* **Đầy đủ chế độ đọc**: Hỗ trợ đọc từ Phải sang Trái (Manga Nhật Bản), Trái sang Phải (Webtoon/Comic) và Fit Width.
* **Bộ nhớ đệm tải trước (Pre-cache)**: Tùy chỉnh tải trước từ 1 đến 10 trang giúp lật trang tức thì không phải chờ mạng.
* **Hỗ trợ phím cứng lật trang**: Tương thích hoàn hảo với Phím âm lượng, Phím mũi tên (Boox/Meebook), Phím Page Turn vật lý.

### 4. 👤 Quản Lý Cá Nhân & Khóa Bảo Vệ
* **Đăng nhập MangaDex linh hoạt**:
  * Hỗ trợ đăng nhập trực tiếp qua **Personal API Client** (TK & MK không cần WebView / Captcha).
  * Đăng nhập qua WebAuth chính thức hoặc dán Session Token / Refresh Token trực tiếp.
  * Tự động làm mới phiên đăng nhập (Auto Token Refresh) không bị gián đoạn.
* **Đồng bộ tiến độ đọc (Auto Sync)**: Tự động đánh dấu chapter đã đọc lên tài khoản MangaDex.
* **Khóa bảo vệ ứng dụng (App Lock)**: Khóa ứng dụng bằng mã PIN 4-6 chữ số, bảo vệ sự riêng tư khi cho mượn máy đọc sách.
* **Bộ lọc nội dung (Content Ratings)**: Tùy chỉnh hiển thị hoặc ẩn các nội dung *Safe, Suggestive, Erotica, Pornographic*.

### 5. 🌐 Vượt Chặn Mạng & Quản Lý Lưu Trữ
* **Tích hợp DNS over HTTPS (DoH)**: Sử dụng DNS bảo mật của Cloudflare (1.1.1.1) hoặc Google (8.8.8.8) để vượt chặn nhà mạng mà không cần cài VPN.
* **Tùy biến Reverse Proxy / Cloudflare Worker**: Hỗ trợ URL proxy riêng nếu cần.
* **Quản lý lưu trữ thông minh**: Nhận diện và cho phép chọn vị trí lưu truyện trên Bộ nhớ trong hoặc Thẻ nhớ ngoài (MicroSD card) kèm hiển thị dung lượng trống thực tế.

---

## 📲 Cài Đặt (Installation)

1. Tải file cài đặt **`InkDex-v1.0.apk`** từ tab [Releases](../../releases).
2. Chép file APK vào máy đọc sách qua cáp USB-C hoặc gửi qua Wifi / Cloud Drive / Telegram.
3. Trên máy đọc sách (ví dụ Bigme B751C S), mở ứng dụng **Quản lý tệp (Files / File Manager)**, chọn file APK và nhấn **Cài đặt**.
4. Mở app **InkDex**, vào **Cài đặt** để cấu hình tài khoản và bắt đầu đọc truyện!

---

## 🛠️ Công Nghệ Sử Dụng (Tech Stack)

* **Ngôn ngữ**: Kotlin (100%)
* **Giao diện (UI)**: Jetpack Compose & Material 3 (Tối ưu hóa đơn sắc & tương phản cao cho E-Ink)
* **Xử lý ảnh (Image Loading)**: Coil 3 kết hợp ColorMatrix Filter tối ưu Kaleido 3
* **Mạng (Networking)**: OkHttp 3 + DNS-over-HTTPS (DoH) + Kotlinx Serialization
* **Lưu trữ (Storage)**: Android Storage Access Framework (SAF) & SharedPreferences
* **Phần cứng E-Ink**: Bigme EPD Hardware Broadcast SDK & Android Choreographer Frame Sync

---

## ⚖️ Tuyên Bố Miễn Trừ Trách Nhiệm & Bản Quyền

* Dữ liệu truyện tranh và hình ảnh được truy xuất trực tiếp từ API mở của **MangaDex** ([api.mangadex.org](https://api.mangadex.org)).
* Toàn bộ bản quyền tác phẩm, tranh vẽ và bản dịch thuộc về tác giả gốc, nhà xuất bản và các nhóm dịch (Scanlation Groups).
* **InkDex** là ứng dụng mã nguồn mở phi thương mại, không lưu trữ truyện trên bất kỳ máy chủ riêng nào và tuân thủ đầy đủ điều khoản dịch vụ của MangaDex.

---

## 📄 Giấy Phép (License)

Dự án được phát hành theo giấy phép [MIT License](LICENSE).
