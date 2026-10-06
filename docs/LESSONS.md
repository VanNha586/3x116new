## [2026-10-06] Lỗi NoSuchMethodError Files.readString trên JDK 8
- **Triệu chứng:** Khi chạy JUnit tests trên JRE 1.8 (Java 8), ứng dụng văng lỗi `java.lang.NoSuchMethodError: java.nio.file.Files.readString`.
- **Nguyên nhân gốc:** `Files.readString` và `Files.writeString` chỉ có từ Java 11+, trong khi runtime của dự án dùng JRE 1.8 (`jre/bin/java.exe`).
- **Cách sửa:** Chuyển sang dùng `Files.readAllBytes` và `Files.write` tương thích hoàn toàn với Java 8 trong [FishingLogModel.java](file:///F:/apps/3x116/src/main/java/com/fishing/models/FishingLogModel.java).
- **Quy tắc:**Luôn đảm bảo code Java tuân thủ chuẩn Java 8 target và kiểm tra test trên đúng JRE embedded của ứng dụng.
- **Test:** [FishingLogModelTest.java](file:///F:/apps/3x116/src/test/java/com/fishing/models/FishingLogModelTest.java) và [FishingServiceTest.java](file:///F:/apps/3x116/src/test/java/com/fishing/services/FishingServiceTest.java)