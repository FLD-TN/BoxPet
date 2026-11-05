# Changelog

## 0.7.3 (2026-11-05)

- Pet dùng kỹ năng thì chat của chủ hiện "Tên pet: Tên kỹ năng!". Bật/tắt: /pet thongbao (bảng mới boxpet_options).
- Kỹ năng mạnh hơn: config.yml abilities.damage-multiplier 1.4 (sát thương), abilities.heal-multiplier 1.3 (hồi máu, khiên). Lore tự cập nhật.
- Kỹ năng kinh tế: nhiệm vụ ghi theo % ("Tăng 30% phần thưởng khi hoàn thành nhiệm vụ"), đào mỏ / hạ mob ghi hệ số (x2, x1,3).

## 0.7.2 (2026-11-05)

- Lore kỹ năng: tên kỹ năng nằm trên dòng "Kỹ năng: <tên>" (menus.yml pet-card.ability-header), dòng trống, rồi mô tả và hồi chiêu.

## 0.7.1 (2026-11-05)

- EXP dư khi chạm mốc đột phá được giữ lại và cộng vào cấp sau khi đột phá thành công (lore: "Cần đột phá (dư X EXP)"). Thất bại vẫn giữ.
- Tự động cho ăn khi chưa có quyền: "✘ Chưa mở khoá".

## 0.7.0 (2026-11-05)

- Đột phá: ở cấp 9, 19, 29, 39, 49, 59 (nhỏ hơn cấp tối đa của độ hiếm; config breakthrough.levels) pet đầy EXP thì đứng lại, kẹo báo lỗi. Dùng Đá Đột Phá (tỉ lệ 1-100% nằm trên từng viên, /boxpet give breakthrough <người> <tỉ lệ> [số]): thành công lên cấp kế tiếp, thất bại mất đá. Dùng bằng cầm đá chuột phải vào pet, hoặc nút Đột phá trong Quản lý pet (ô 32, chỉ hiện khi cần). Lore EXP hiện "Cần đột phá".
- Tự động cho ăn chỉ cho người có quyền boxpet.autofeed (rank Champion trở lên). Nút trong Quản lý pet: chuột trái bật/tắt, chuột phải mở hộp thức ăn.
- Sửa lỗi: cầm kẹo/thức ăn chuột phải ở đâu cũng cho pet ăn. Giờ phải chuột phải vào pet.

## 0.6.0 (2026-11-05)

- Tự động cho ăn: nút mới trong Quản lý pet (ô 39), mở hộp thức ăn 21 ô dùng chung cho mọi pet. Bấm kẹo/thức ăn trong túi đồ để bỏ vào, bấm ô trong hộp để lấy ra. Chọn mức bắt đầu ăn (độ no 60 / 50 / 40 / 30 / 20 / 10) và ưu tiên Kẹo trước / Thức ăn trước. Pet đang thả tới mức đó thì ăn tới khi no, mỗi loại theo thứ tự ô từ trái qua phải. Hộp và túi đồ được lưu cùng lúc (không nhân đồ khi crash). Bảng mới boxpet_autofeed.
- EXP pet chỉ từ kẹo: config.yml xp.from-activities (mặc định false) tắt mọi nguồn trong xp-sources.yml.
- Vuốt ve: +1 thân thiết (bond.pet-gain mặc định 1), 4 giờ một lần; bấm lúc đang chờ chỉ hiện thời gian chờ, không còn hiệu ứng / câu nói.
- Kho pet mặc định 1 ô (storage-slots.default); boxpet.storage.<số> = tổng số ô.

## 0.5.4 (2026-11-05)

- Lore kỹ năng: "Kỹ năng:", dòng trống, tên dạng "- Tên kỹ năng", dòng mô tả đầu có •, các dòng sau thụt vào; Hồi chiêu giữ nguyên.
- "[Đang được thả]" chuyển lên tên item thay vì nằm trong lore.
- Dòng cảnh báo pet đói nằm dưới kỹ năng (cách 1 dòng trống), trên các dòng [Chuột ...]. menus.yml cũ không có %warning% thì vẫn hiện ở cuối.
- Tên trên đầu pet: [Lv.X] cùng màu với cấp độ trong lore (%level_colour%). Server cũ: sửa visibility.nametag trong config.yml để dùng.
- /pet: Shift + chuột trái vào pet đang thả để cất; bỏ nút Cất pet; Ẩn pet người khác và Trò chuyện nhích sang trái 1 ô.
- Quản lý pet: ô 30/31 cho ăn, vuốt ve; 33/34 ngồi yên, đặt tên; 41 thả/cất pet; tiến hóa ở ô 43 (đếm từ 1).

## 0.5.3 (2026-11-05)

- Màu tên pet làm lại: Thường và Hiếm một màu, mỗi pet một vùng màu riêng; Sử thi gradient 2-5 màu; Huyền thoại gradient 3-4 màu.
- Rồng Cubee đổi thành Tinh Linh (Tinh Linh Lửa, Tinh Linh Đất...); kỹ năng Tinh Linh May Mắn.

## 0.5.2 (2026-11-05)

- Pet đói (độ no 0) mất cả chỉ số lẫn kỹ năng; còn ít nhất 1 điểm no mới có buff. Lore có dòng cảnh báo canh giữa ở cuối.
- Thông báo lỗi màu đỏ (#FF4D4D); thông báo đang giao tranh / cần thả pet đổi sang ✘ đỏ.
- Kỹ năng: dòng "Kỹ năng:", tên trong 〈 〉, mô tả dạng câu như cũ với số có màu, dòng Hồi chiêu riêng.
- Chỉ số MMOItems bỏ khoảng trắng sau 」; Thư viện hiện "Chỉ số cơ bản" (cấp 1); lore pet có dòng trống sau tên.
- Thân thiết / độ no chỉ hiện số, cùng màu thanh (hồng / cam).
- Bỏ nút sắp xếp (luôn theo độ hiếm tăng dần), 2 ô lọc ở góc phải; nút trang dùng mũi tên vàng.
- /pet 54 ô, Thư viện ở giữa hàng đầu; Quản lý pet bỏ nút trò chuyện, nút thả pet đi ở ô cuối.
- BALANCE.md: bảng chỉ số cấp 1 → tối đa của cả 52 pet.

## 0.5.0 (2026-11-05) — làm mới giao diện và dữ liệu pet

### Thay đổi lớn
- **Một model = một pet = một độ hiếm.** Mỗi file pet có `rarity`; trứng của một pet luôn ra đúng độ hiếm đó,
  Trứng Ngẫu Nhiên chọn trong các pet cùng độ hiếm. Lệnh: `give egg <người> <pet> [số]`, `give pet <người> <pet> [cấp]`,
  bỏ `set rarity`. Độ hiếm càng cao, chỉ số gốc càng cao (`stat-multiplier`).
- Mỗi pet có màu tên riêng (`color` trong file pet, hex hoặc gradient). Độ hiếm có màu và gradient mới.
- **Bỏ tính năng ẵm pet.**
- Menu chi tiết và chăm sóc gộp thành **Quản lý pet**. Cho ăn được cả pet đang nằm trong kho; vuốt ve, trò chuyện,
  ngồi yên cần thả pet ra (có thông báo). Nút "Thả pet đi" đứng riêng, có viền đỏ.
- Thêm **Thư viện thú cưng** trong /pet: mọi loại pet, chỉ số từ cấp 1 tới cấp tối đa, kỹ năng.
- Bộ lọc mới kiểu AxAuctions cho Kho pet, Bộ sưu tập, Thư viện: lọc loại, lọc độ hiếm, 4 kiểu sắp xếp
  (chuột trái kiểu sau, chuột phải kiểu trước).

### Lore
- Thẻ pet chia mục: dòng độ hiếm và loại, Phát triển (Cấp độ có màu theo mốc 10, kinh nghiệm), Tình trạng
  (thân thiết, độ no, tuổi tính chính xác từ lúc nở), Chỉ số cộng thêm (icon và màu theo MMOItems), Kỹ năng.
- Kỹ năng có tên màu riêng, mô tả một câu, các dòng: kiểu, kích hoạt, sát thương, hiệu ứng, tầm, hồi chiêu...
- Bỏ mô tả pet; bộ sưu tập chỉ hiện độ hiếm và điểm; bỏ dòng thuộc tính gốc của item ("+7 Armor").
- Lời thoại nhiều hơn, câu riêng theo từng họ pet trộn chung với câu chung, xưng "chủ nhân".

### Nâng cấp từ 0.4.x
- Nếu server đã chạy bản cũ: xoá thư mục `plugins/BoxPet/pets/` và các file `menus.yml`, `rarities.yml`,
  `abilities.yml`, `dialogues.yml` để BoxPet tạo bản mới (file cũ không bị ghi đè).

## 0.4.2 (2026-11-05)

- Mặc định nhẹ hơn: `visibility.view-distance` 24 (trước 32), `visibility.max-visible-per-player` 10 (trước 15).
  File config.yml đã có sẵn trên server giữ số cũ: sửa 2 dòng đó hoặc xoá đi để dùng mặc định mới.
- `abilities.knockback` (mặc định false): đòn kỹ năng của pet không đẩy lùi mục tiêu.

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
