# Changelog

## 0.4.1 (2026-11-05) — rà soát hiệu suất và lỗi

### Sửa lỗi
- Sát thương pet bỏ qua thời gian bất tử của mục tiêu rồi trả lại như cũ: không bị nuốt mất, không làm yếu đòn kế
  tiếp của chủ.
- Không còn giữ được cả vật phẩm lẫn hiệu quả khi server crash: túi đồ được lưu cùng lúc với dữ liệu pet sau khi
  cho ăn kẹo / thức ăn, và lưu ngay khi đặt trứng vào lò.
- Kẹo, thức ăn, trứng bị trừ bằng cách ghi lại đúng ô trong túi đồ, không dựa vào tham chiếu vật phẩm.
- Tên pet: ai cũng dùng được màu và gradient; cấm `%` (placeholder), `#` chỉ trong mã màu hex, chỉ cho phép thẻ
  màu và kiểu chữ. Bỏ quyền `boxpet.rename.color`.
- Rò rỉ RAM: danh sách mob MyBoxMobs đã xử lý luôn được dọn sau 2 tick; các bộ đếm theo người chơi được dọn khi thoát
  (thời gian chờ đang chạy vẫn giữ, thoát vào lại không lách được).
- Tên trên đầu pet tự tạo lại nếu bị server xoá (chunk unload).
- Thời gian hồi kỹ năng và khiên được giữ theo từng pet: cất rồi thả lại không reset.

### Hiệu suất
- Kết quả đọc block (mặt đất, chỗ trống) được dùng lại tới khi chủ sang block khác hoặc sau 10 tick.
- Tên trên đầu pet di chuyển mỗi 3 tick có nội suy (`visibility.nametag-update-ticks`), thay vì mỗi tick.
- Kỹ năng chiến đấu chưa bắn được (mục tiêu xa, bị che, vùng cấm) chờ 0,5 giây mới thử lại; kiểm tra khoảng cách
  và tầm nhìn trước, WorldGuard sau cùng.

## 0.4.0 (2026-11-05) — bản đầu, đủ 4 giai đoạn của spec

### 0.1 Nền tảng
- Độ hiếm (`rarities.yml`), 52 pet mẫu (`pets/`), lưu SQLite / MySQL.
- Trứng cố định và Trứng Ngẫu Nhiên theo độ hiếm; lò ấp tính giờ cả khi offline, ấp ngay bằng gem, mở trứng có
  hiệu ứng quay gacha; kho pet có lọc và sắp xếp.
- Pet là model ModelEngine trên dummy: 3 chế độ Nghỉ / Đi theo / Giao tranh, nút Ngồi yên, xử lý elytra, cưỡi thú,
  dịch chuyển, ender pearl, đổi thế giới, chết, thoát game.
- Chỉ số MMOItems qua MythicLib (key `boxpet`), ẩn pet người khác, giới hạn số pet nhìn thấy, đổi tên.
- Lệnh admin: give, pets (cả offline), transfer, set, delete / restore, incubator clear, models, reload; `admin.log`.

### 0.2 Nuôi pet
- Level theo `levels.yml` (công thức hoặc bảng), XP hoạt động có giới hạn mỗi ngày (`xp-sources.yml`).
- 4 loại kẹo; độ no dùng chung cho kẹo và thức ăn, no thì không ăn được.
- Chuột phải vào pet: menu chăm sóc (vuốt ve, cho ăn, trò chuyện, ngồi yên, ẵm); Shift + chuột phải: ẵm pet.
- Thân thiết: +5 mỗi 4 giờ khi vuốt ve, -5 mỗi 24 giờ bị bỏ bê, quyết định % chỉ số.
- Tuổi pet, sinh nhật; pet nói chuyện với chủ (chat + bong bóng chữ).
- Khoá menu khi giao tranh; bộ sưu tập và mốc thưởng; tiến hóa (sẵn code, chưa có model bậc sau); thả pet.

### 0.3 Kỹ năng
- Chiến đấu: projectile (đạn VFX bám mục tiêu), strike (VFX tại mục tiêu), mark (đánh dấu tăng sát thương).
- Hỗ trợ: heal, shield (hấp thụ), potion (giữ 1 hiệu ứng tốt).
- Chỉ đánh người chủ vừa đánh hoặc người vừa đánh chủ; không đánh cùng clan, vùng cấm PvP / flag
  `boxpet-ability`, NPC, người tàng hình. Sát thương PvP x0,6. Không kích hoạt enchant Crazy Enchantments.
- 21 kỹ năng mẫu dùng VFX của các pack.

### 0.4 Kinh tế
- Pet kinh tế cộng hệ số BoxCore (mining / quest / mob) qua API nguồn cộng thêm của BoxCore 1.6.0.
- Placeholder PlaceholderAPI, `BoxPetService` và 6 sự kiện cho plugin khác.
