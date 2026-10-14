## [2026-10-06] Lỗi NoSuchMethodError Files.readString trên JDK 8
- **Triệu chứng:** Khi chạy JUnit tests trên JRE 1.8 (Java 8), ứng dụng văng lỗi `java.lang.NoSuchMethodError: java.nio.file.Files.readString`.
- **Nguyên nhân gốc:** `Files.readString` và `Files.writeString` chỉ có từ Java 11+, trong khi runtime của dự án dùng JRE 1.8 (`jre/bin/java.exe`).
- **Cách sửa:** Chuyển sang dùng `Files.readAllBytes` và `Files.write` tương thích hoàn toàn với Java 8 trong [FishingLogModel.java](file:///F:/apps/3x116/src/main/java/com/fishing/models/FishingLogModel.java).
- **Quy tắc:**Luôn đảm bảo code Java tuân thủ chuẩn Java 8 target và kiểm tra test trên đúng JRE embedded của ứng dụng.
- **Test:** [FishingLogModelTest.java](file:///F:/apps/3x116/src/test/java/com/fishing/models/FishingLogModelTest.java) và [FishingServiceTest.java](file:///F:/apps/3x116/src/test/java/com/fishing/services/FishingServiceTest.java)

## [2026-10-15] Không lưu được thông tin tài khoản (username/password) khi offline/local
- **Triệu chứng:** Người dùng nhập tài khoản/mật khẩu trong phần Cài đặt và bấm Lưu, nhưng khi tắt tool và mở lại qua `Chay-Tool.bat` thì bảng tài khoản bị rỗng, phải nhập lại bằng tay.
- **Nguyên nhân gốc:**
  1. Trường `avt.Q.W` là role của tài khoản đăng nhập tool (yêu cầu giá trị `"user"`). Trước đó bị gán nhầm thành chuỗi hạn sử dụng `"Không giới hạn (Vĩnh viễn)"`. Trong hàm lưu tài khoản `avt.Q.Y`, điều kiện `if (!"user".equals(W) && anyMatch(acc -> acc.M > 0)) return;` đã chặn không cho ghi dữ liệu.
  2. Cơ chế gốc của phần mềm chỉ gọi `avt.Q.Y` (ghi file `data/client-accounts.json`) sau khi nhận phản hồi HTTP 200 từ cloud API (`avt.Q.vi`). Khi chạy local/offline bypass, API này không bao giờ phản hồi nên thao tác lưu không bao giờ ghi xuống đĩa trước khi thoát tiến trình (`System.exit(0)`).
  3. Khi chạy qua `Chay-Tool.bat`, CWD là thư mục `app/`, trong khi chạy từ script test thì CWD là thư mục gốc. Việc dùng `new File(".").getAbsoluteFile().getName()` trả về `.` chứ không phải `app`, dẫn tới sai lệch đường dẫn tương đối tạo thư mục lồng `app/app/`.
- **Cách sửa:**
  1. Gán đúng `avt.Q.W = "user"`, đồng thời đưa chuỗi hạn dùng về đúng các trường `avt.Q.O` và `avt.Q.l`.
  2. Bổ sung `BypassHelper.saveClientAccounts()` tự động mã hóa mật khẩu (`avt.B.n`) và lưu trực tiếp xuống file `data/client-accounts.json` mỗi khi gọi `getStateJson()` hoặc khi nhấn Lưu.
  3. Thêm `AccountSaverShutdownHook` để flush dữ liệu khi JVM đóng và `syncAccountFilesOnStartup()` để đồng bộ giữa thư mục gốc và thư mục `app/data/`.
  4. Sử dụng `getCanonicalFile()` để kiểm tra đúng tên thư mục làm việc (`isRunningInAppDir()`).
- **Quy tắc:** Mọi can thiệp vào các trường cấu hình của `avt.Q` phải kiểm tra kỹ tác động phụ tới luồng logic của các phương thức phụ thuộc (`Y`, `v5`). Luôn dùng `getCanonicalFile()` thay vì `getAbsoluteFile()` khi kiểm tra tên thư mục hiện tại.
- **Test:** [test_account_persistence_regression.py](file:///F:/apps/3x116/tests/test_account_persistence_regression.py)