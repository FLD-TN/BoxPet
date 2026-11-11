# BoxPet

Thú cưng 3D cho server BoxPvP. Pet là model ModelEngine gắn trên một "dummy" chỉ tồn tại bằng packet, không phải mob
thật: không chắn đòn, không bị đánh, không kẹt địa hình. Mọi pet cộng chỉ số MMOItems cho chủ và có thêm một vai trò:
chiến đấu, hỗ trợ hoặc kinh tế.

Paper 1.21.1, Java 21. Cần ModelEngine R4.1.1 và MythicLib (MMOItems 6.10). Không bắt buộc: BoxCore (pet kinh tế,
Gem), BoxQuest (XP khi làm nhiệm vụ), BoxClans, MyBoxMobs, CataMines, WorldGuard, Vault, PlaceholderAPI, ItemsAdder.

## Có những gì

- **Ấp trứng.** Trứng của từng pet nở vào kho. Trứng ngẫu nhiên mở ngay trong lò ấp ra trứng của một pet cụ thể. Mỗi
  trứng có số seri riêng, chỉ dùng được một lần (bản sao chép lậu bị từ chối). Pet chỉ trao đổi được ở dạng trứng.
- **Vai trò.** Pet chiến đấu tự đánh mục tiêu của chủ bằng kỹ năng có VFX riêng. Pet hỗ trợ tạo khiên, hồi máu, đánh
  dấu, tăng chỉ số cho chủ. Pet kinh tế cộng thêm hệ số boost của BoxCore (quest, mine, mob).
- **Kỹ năng.** Mỗi pet có đúng một kỹ năng. Thời gian hồi theo độ hiếm và chỉ bắt đầu đếm khi kỹ năng chạy xong. Đổi
  pet không làm mới thời gian hồi.
- **Chế độ.** Mỗi người chọn pet Tấn công / Phòng thủ / Thân thiện và mục tiêu Tất cả / Người / Quái vật
  (`/pet chedo`, `/pet muctieu` hoặc trong menu). Sát thương của pet tính là của chủ.
- **Cấp và đột phá.** Pet lên cấp bằng EXP (kẹo, thức ăn EXP, hoạt động, nhiệm vụ). Tới các mốc đột phá thì pet đứng
  lại, cần Đá Đột Phá (có tỉ lệ thành công) mới lên tiếp. Mỗi lần đột phá cộng thêm % chỉ số. Một số pet có các bậc tiến
  hoá đổi model.
- **Chăm sóc.** Độ no giảm theo giờ thật với mọi pet (đang thả, trong kho, chủ offline, cả lúc server tắt); pet đói hẳn
  không cho chỉ số. Độ thân thiết tăng khi vuốt ve và cho ăn, giảm khi bị bỏ bê; mức thân thiết nhân chỉ số pet
  (75% đến 110%).
- **Bộ sưu tập.** Tính theo số loại pet đã có, mỗi mốc cho thêm chỉ số (`collection.yml`).
- **Hiển thị.** Pet nói chuyện theo sự kiện và theo họ pet (`dialogues.yml`), có thể tắt. Ẩn pet của người khác bằng
  `/pet hideothers`; ẩn luôn cả VFX kỹ năng của họ. Khoe pet trong chat qua InteractiveChat (`[pet]`), người khác bấm
  vào để xem thẻ pet.
- **Khoá giao tranh.** Đang đánh nhau thì không mở menu, không gọi / cất / đổi pet được (`combat-tag-seconds`).

Pet mẫu: 52 pet trong `pets/`, mỗi file một pet (họ, vai trò, model, bậc tiến hoá, chỉ số, kỹ năng). Thư mục này chỉ
được tạo một lần; pet bạn xoá khỏi thư mục sẽ không tự quay lại.

## Cài đặt

1. Bỏ jar `BoxPet-<version>.jar` vào `plugins/`.
2. Model pet và model VFX (`.bbmodel`) là pack mua riêng, không có trong repo. Chép các blueprint vào
   `plugins/ModelEngine/blueprints/`, icon ItemsAdder của pack vào `plugins/ItemsAdder/contents/`. Không cần cài phần
   MythicMobs / MCPets / Nexo / Oraxen đi kèm pack.
3. `/meg reload`, `/iazip`, rồi `/boxpet models` để kiểm tra đã đủ model cho mọi pet.

## Lệnh

| Lệnh | Quyền | Mô tả |
|---|---|---|
| `/pet` (`/pets`, `/thucung`) | `boxpet.use` | Menu chính: kho, lò ấp, bộ sưu tập |
| `/pet dismiss` | `boxpet.use` | Cất pet đang thả |
| `/pet chedo <tancong\|phongthu\|thanthien>` | `boxpet.use` | Chế độ của pet |
| `/pet muctieu <tatca\|nguoi\|quaivat>` | `boxpet.use` | Mục tiêu của pet |
| `/pet hideothers`, `/pet chat`, `/pet thongbao` | `boxpet.use` | Ẩn pet người khác, tắt pet nói chuyện, tắt tin báo kỹ năng |
| `/pet rename <tên>` | `boxpet.rename` | Đặt tên, dùng được màu và gradient |
| `/boxpet give egg <người> <pet> [số]` | `boxpet.admin` | Trứng của một pet |
| `/boxpet give randomegg <người> <độ hiếm> [số]` | `boxpet.admin` | Trứng ngẫu nhiên |
| `/boxpet give candy\|food <người> <mã> [số]` | `boxpet.admin` | Kẹo / thức ăn trong `items.yml` |
| `/boxpet give breakthrough <người> <1-100> [số]` | `boxpet.admin` | Đá Đột Phá với tỉ lệ thành công |
| `/boxpet give pet <người> <pet> [cấp]` | `boxpet.admin` | Cho pet thẳng vào kho |
| `/boxpet pets <người>` | `boxpet.admin` | Xem kho của người chơi (cả offline): chuyển, sửa, xoá, khôi phục |
| `/boxpet transfer <mã> <người>` | `boxpet.admin` | Chuyển pet |
| `/boxpet set <mã> level\|xp\|bond\|fullness <giá trị>` | `boxpet.admin` | Sửa pet |
| `/boxpet delete\|restore <mã>` | `boxpet.admin` | Xoá mềm / khôi phục |
| `/boxpet incubator <người> clear <ô>` | `boxpet.admin` | Dọn một ô lò ấp |
| `/boxpet models`, `/boxpet reload` | `boxpet.admin` | Kiểm tra model, đọc lại file |

`<mã>` là 8 ký tự đầu của mã pet (hiện trong menu admin, có gợi ý tab). Mọi thao tác admin ghi vào `admin.log`.

Quyền khác: `boxpet.storage.<số>` (số ô kho, lấy số lớn nhất), `boxpet.incubator.<số>` (số ô lò ấp),
`boxpet.incubator.skip` (ấp xong ngay, miễn phí), `boxpet.bypass` (bỏ qua khoá giao tranh và thời gian chờ, op có sẵn).

## Placeholder

| Placeholder | Giá trị |
|---|---|
| `%boxpet_has_active%` | Đang thả pet không |
| `%boxpet_active_name%`, `%boxpet_active_name_plain%`, `%boxpet_active_name_level%` | Tên pet đang thả |
| `%boxpet_active_type%`, `%boxpet_active_level%`, `%boxpet_active_rarity%`, `%boxpet_active_role%` | Loại, cấp, độ hiếm, vai trò |
| `%boxpet_active_bond%`, `%boxpet_active_fullness%`, `%boxpet_active_age%` | Thân thiết, độ no, tuổi |
| `%boxpet_active_id%`, `%boxpet_active_card%`, `%boxpet_active_card_<n>%` | Dùng cho InteractiveChat |
| `%boxpet_collection_count%`, `%boxpet_collection_total%` | Số loại pet đã có / tổng số loại |
| `%boxpet_storage_used%`, `%boxpet_storage_max%` | Ô kho đã dùng / tối đa |

Pet kinh tế cộng vào boost BoxCore; xem bằng `%boxcore_bonus_<loại>%`.

## File cấu hình

| File | Nội dung |
|---|---|
| `config.yml` | Lưu trữ (SQLite hoặc MySQL), giao tranh, đi theo, đột phá, lò ấp, thân thiết, độ no, đổi tên, pet chat |
| `rarities.yml` | Độ hiếm: nhân chỉ số, cấp tối đa, thời gian ấp |
| `pets/*.yml` | Từng pet |
| `abilities.yml` | Kỹ năng |
| `levels.yml`, `xp-sources.yml` | EXP lên cấp, EXP từ hoạt động và giới hạn mỗi ngày |
| `items.yml` | Kẹo, thức ăn, hình trứng |
| `collection.yml` | Mốc bộ sưu tập |
| `dialogues.yml`, `messages.yml`, `menus.yml` | Câu pet nói, tin nhắn, menu |

Nâng cấp chỉ cần thay jar: thiếu khoá nào thì dùng mặc định trong jar, file trên server không bị ghi đè.

## API

Gói `com.dex.boxpet.api` (module `boxpet-api`). `BoxPetService` qua ServicesManager: `getActivePet`, `getPets`,
`addXp`, `feed`, `bonusTenths`. Sự kiện: `PetSummonEvent` (huỷ được), `PetDismissEvent`, `PetHatchEvent`,
`PetLevelUpEvent`, `PetEvolveEvent`, `PetAbilityDamageEvent` (huỷ được, sửa được sát thương).

## Build

```
./gradlew build
```

Ra file `boxpet-plugin/build/libs/BoxPet-<version>.jar`. Repo chỉ chứa mã nguồn, không có jar nào ngoài
`gradle/wrapper/gradle-wrapper.jar` của Gradle. Trước khi build, đặt vào thư mục `libs/`:

- `ModelEngine-R4.1.1.jar`: jar plugin ModelEngine
- `MythicLib-1.7.jar`: jar plugin MythicLib
- `boxcore-api-1.4.0.jar`: build từ [BoxCore](https://github.com/FLD-TN/BoxCore) (`boxcore-api/build/libs/`)
- `boxclans-api-1.4.4.jar`: build từ [BoxClans](https://github.com/FLD-TN/BoxClans) (`boxclans-api/build/libs/`)
