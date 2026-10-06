## [2026-10-06] Gọi API bị lỗi 401 sau khi đăng nhập
- Nguyên nhân: token lưu ở localStorage nhưng interceptor đọc từ cookie.
- Quy tắc: mọi request phải đi qua `src/api/client.ts`, không dùng fetch trực tiếp.
- Test: `tests/api/auth.test.ts`