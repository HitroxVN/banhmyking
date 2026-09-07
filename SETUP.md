# SETUP.md — Hướng dẫn chạy môi trường dự án

> Cho thành viên mới vào dự án. Làm theo từng bước, xong mục **Verify** là máy bạn sẵn sàng code.

---

## 1. Yêu cầu

| Phần mềm | Version | Ghi chú |
|---|---|---|
| JDK | **21** | Project target Java 17, nhưng team thống nhất dùng JDK 21. ⚠️ **Không dùng JDK 11** — TLS cũ không bắt tay được Maven Central (đã dính lỗi này) |
| MySQL | 8.x / 9.x | Port **3307** (không phải 3306 mặc định) |
| Git | bất kỳ | |
| IDE | IntelliJ / VS Code | IntelliJ khuyên dùng (Ultimate có hỗ trợ Spring; Community vẫn chạy được) |

**Kiểm tra JDK:**

```bash
java -version          # phải ra 21.x
echo $JAVA_HOME        # phải trỏ tới thư mục JDK 21
```

Nếu `JAVA_HOME` trỏ JDK 11 → đổi: *System Properties → Environment Variables → JAVA_HOME* → đường dẫn JDK 21, rồi **mở lại terminal/IDE** (biến env không tự refresh).

Maven **không cần cài** — repo đã có wrapper `mvnw`.

---

## 2. Clone & cấu hình secret

```bash
git clone https://github.com/HitroxVN/banhmyking.git banhmyking
cd banhmyking
```

Đổi teen file `application-dev.properties.example` thành `application-dev.properties`

```bash
cp src/main/resources/application-dev.properties.example \
   src/main/resources/application-dev.properties
```

Mở file vừa copy, sửa 2 dòng theo máy bạn:

```properties
spring.datasource.username=root
spring.datasource.password=<password MySQL máy bạn>
```

> File này là cấu hình riêng từng mays tránh conflict.

---

## 3. Tạo database

MySQL phải chạy ở port **3307**. Kết nối vào MySQL rồi tạo:

```sql
CREATE DATABASE banhmyking
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

> Charset bắt buộc `utf8mb4` — thiếu là mất tiếng Việt + emoji.
> Nếu MySQL của bạn chỉ chạy được port 3306: sửa `spring.datasource.url` trong `application-dev.properties` cho `3306`

> * Lưu ý: KHÔNG TỰ TẠO BẢNG BẰNG FILE. KHI CHẠY LẦN ĐẦU CÁC BẢNG SẼ TỰ ĐỘNG ĐƯỢC MIGRATE VÀO DB.

---

## 4. Chạy dự án

```bash
./mvnw clean verify       # build + test
./mvnw spring-boot:run    # chạy app
```

Lần chạy đầu Maven tải dependency (vài phút, có màn hình "Downloading...") — bình thường, các lần sau nhanh.

---

## 5. Verify — máy bạn đã sẵn sàng khi:

| Bước | Log                                        |
|---|--------------------------------------------|
| `./mvnw clean verify` | `BUILD SUCCESS`, 0 test fail               |
| `./mvnw spring-boot:run` | log có `Started BanhmykingApplication`     |
| `curl http://localhost:8080/api/v1/health` | JSON `{"success":true,"message":"OK",...}` |

Cả 3 xanh → OK.

## LỖI THÌ CHỊU. HỎI CHAT.
