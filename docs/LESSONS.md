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

## [2026-10-15] Bấm nút "Dừng" nhưng bot vẫn không dừng hoặc tự kết nối lại
- **Triệu chứng:** Người dùng bấm nút "Dừng" ở dòng tài khoản hoặc nút "Dừng tất cả", nhưng bot vẫn tiếp tục câu hoặc tự động kết nối lại game server, giao diện vẫn hiển thị "Đang chạy".
- **Nguyên nhân gốc:**
  1. `avt.game.k.Sd` (phương thức tự kết nối lại) có bytecode (offset 63-65) trực tiếp gán `this.hl = false`. Khi socket bị đóng bởi lệnh dừng `bot.N()`, nếu scheduler reconnect (`avt.game.O.b`) đã được lên lịch từ trước thì nó vẫn kích hoạt `Sd()`, vô hiệu hóa cờ dừng `hl`.
  2. Trong `BypassHelper.doQuangCau`, tác vụ delay 1.0 giây quăng câu tiếp theo không được lưu vào `pendingCastMap`, do đó khi `cancelPendingCast` được gọi, tác vụ 1s này không bị hủy và tiếp tục gửi packet bắt đầu lượt câu 45/41.
  3. Trong `avt.game.O.handleDisconnected`, khi bot dừng, listener reconnect executor (`O.H`) và `ScheduledFuture` (`O.b`) không bị shutdown/cancel triệt để.
  4. Trong `BypassHelper.getStateJson`, khi index đã bị dừng (`stoppedBotIndices`), nếu `bot` trong cache vẫn còn và cờ `hl` bị `Sd()` reset thì trạng thái `isRunning` vẫn trả về `true`.
- **Cách sửa:**
  1. Trong `BypassHelper.stopBotAtIndex`: set `bot.hy` (AtomicBoolean guard reconnect) = `true` để `Sd()` không thể `compareAndSet(false, true)`; hủy timer `F.b`, shutdown `F.H` và `bot.hf`; ngắt socket `J.n()` và ngắt luồng `hX.interrupt()`.
  2. Trong `BypassHelper.doQuangCau`: đưa `ScheduledFuture` của delay 1.0s vào `pendingCastMap` để `cancelPendingCast` hủy ngay lập tức.
  3. Trong `BypassHelper.handleDisconnected`: kiểm tra `isStopped` (bao gồm `isStopping` và `stoppedBotIndices`), hủy ngay `listener.b`, shutdown `listener.H` và gọi `bot.S()`, tuyệt đối không gọi `listener.j()` (reconnect).
  4. Trong `BypassHelper.getStateJson`: kiểm tra `stoppedBotIndices.contains(i)`, nếu đã dừng thì gán `bot = null`, `acc.b = false`, trả về `isRunning = false` và `running = false` để UI lập tức chuyển sang "Đã dừng".
- **Quy tắc:** Khi can thiệp cơ chế dừng tiến trình mạng/bot có cơ chế tự phục hồi (auto-reconnect scheduler), phải triệt tiêu cả bộ hẹn giờ (reconnect executor/future) và vô hiệu hóa cờ khởi tạo reconnect (`AtomicBoolean hy`). Mọi tác vụ delay/schedule ngầm phải được track để có thể cancel khi dừng.
- **Test:** [test_stop_behavior_regression.py](file:///F:/apps/3x116/tests/test_stop_behavior_regression.py)