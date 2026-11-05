# Changelog

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
