# BoxPet

Thú cưng 3D cho BoxPvP (Paper 1.21.1). Pet là model ModelEngine trên một "dummy" (chỉ gửi packet, không có mob thật):
không chắn đòn, không bị đánh, không kẹt. Mọi pet cộng chỉ số MMOItems; thêm một vai trò: chiến đấu, hỗ trợ
hoặc kinh tế (cộng hệ số BoxCore). Thiết kế đầy đủ: [BOXPET_SPEC.md](BOXPET_SPEC.md).

## Cần có trên server

| Plugin | Bắt buộc | Dùng cho |
|---|---|---|
| ModelEngine R4.1.1 | có | model pet, model VFX |
| MythicLib 1.7 (MMOItems 6.10) | có | chỉ số, sát thương kỹ năng |
| BoxCore **1.6.0** | không | gem (ấp nhanh, tiến hóa), pet kinh tế (nguồn cộng thêm P) |
| BoxQuest **0.3.2** | không | XP pet khi hoàn thành nhiệm vụ |
| BoxClans, WorldGuard, Vault, PlaceholderAPI, ItemsAdder, CataMines, MyBoxMobs | không | xem spec |

Bản cũ hơn của BoxCore / BoxQuest vẫn chạy, chỉ thiếu tính năng tương ứng (có cảnh báo trong console).

## Cài đặt

1. Bỏ `BoxPet-0.4.0.jar` (trong `boxpet-plugin/build/libs/`) vào `plugins/`. Nâng cấp BoxCore lên
   `BoxCore-1.6.0.jar` và BoxQuest lên `BoxQuest-0.3.2.jar` (chỉ thay jar).
2. **Model** (thư mục `PetModel/`, không có trong git):
   - Chép mọi file `.bbmodel` (cả pet lẫn `vfx_*`) của 4 pack vào `plugins/ModelEngine/blueprints/`.
     Palcat dùng thư mục **RPG use (has utility)**.
   - Chép thư mục `ItemsAdder/contents/...` của từng pack vào `plugins/ItemsAdder/contents/` (icon trứng / pet).
   - **Không** cài các thư mục `MythicMobs/`, `MCPets/`, `Nexo/`, `Oraxen/` của pack (BoxPet không dùng).
   - `/meg reload`, `/iazip`, rồi `/boxpet models`: phải báo đủ model cho cả 52 pet.
3. Khởi động lại. BoxPet tạo `plugins/BoxPet/` với các file cấu hình và thư mục `pets/` (52 pet mẫu).

## Lệnh

| Lệnh | Quyền | |
|---|---|---|
| `/pet` | `boxpet.use` | menu chính |
| `/pet summon <tên>` / `/pet dismiss` | `boxpet.use` | thả / cất pet |
| `/pet hideothers` / `/pet chat` | `boxpet.use` | ẩn pet người khác / bật tắt pet nói chuyện |
| `/pet rename <tên>` | `boxpet.rename` | màu trong tên: `boxpet.rename.color` |
| `/boxpet give egg <người> <pet> <độ hiếm> [số]` | `boxpet.admin` | trứng của một pet |
| `/boxpet give randomegg <người> <độ hiếm> [số]` | `boxpet.admin` | Trứng Ngẫu Nhiên (trứng tân thủ: `common`) |
| `/boxpet give candy\|food <người> <mã> [số]` | `boxpet.admin` | kẹo / thức ăn (`items.yml`) |
| `/boxpet give pet <người> <pet> [độ hiếm] [cấp]` | `boxpet.admin` | thẳng vào kho |
| `/boxpet pets <người>` | `boxpet.admin` | xem kho (cả offline): chuyển, sửa, xoá, khôi phục |
| `/boxpet transfer <mã> <người>` | `boxpet.admin` | |
| `/boxpet set <mã> level\|xp\|rarity\|bond\|fullness <giá trị>` | `boxpet.admin` | |
| `/boxpet delete\|restore <mã>` | `boxpet.admin` | xoá mềm / khôi phục |
| `/boxpet incubator <người> clear <ô>` | `boxpet.admin` | |
| `/boxpet models` / `/boxpet reload` | `boxpet.admin` | |

Quyền thêm ô: `boxpet.storage.<số>` (kho, mặc định 20), `boxpet.incubator.<số>` (lò ấp, mặc định 1, tối đa 5).
`<mã>` là 8 ký tự đầu của mã pet (hiện trong menu admin, có gợi ý tab). Mọi thao tác admin ghi vào `admin.log`.

## File cấu hình

| File | Nội dung |
|---|---|
| `config.yml` | lưu trữ, giao tranh, vị trí và cách đi theo, di chuyển nhanh, ẵm, thân thiết, độ no, đổi tên, pet chat |
| `rarities.yml` | độ hiếm: nhân chỉ số, cấp tối đa, thời gian ấp, điểm sưu tập |
| `pets/*.yml` | mỗi file một pet: tên, họ, vai trò, icon, model / bậc tiến hóa, hoạt ảnh, chỉ số, kỹ năng |
| `abilities.yml` | 21 kỹ năng mẫu cho 12 họ pet |
| `levels.yml` | XP lên cấp: công thức hoặc bảng |
| `xp-sources.yml` | XP từ hoạt động và giới hạn mỗi ngày |
| `items.yml` | kẹo, thức ăn, hình trứng |
| `collection.yml` | mốc điểm sưu tập và chỉ số thưởng |
| `dialogues.yml` | câu pet nói, theo sự kiện và theo họ pet |
| `messages.yml`, `menus.yml` | toàn bộ chữ trong chat và menu |

Nâng cấp chỉ cần thay jar: thiếu dòng nào thì dùng mặc định trong jar, file của bạn không bị ghi đè.
Thư mục `pets/` chỉ được tạo một lần; pet bạn xoá khỏi thư mục sẽ không tự quay lại.

## Placeholder

`%boxpet_active_name%`, `_active_name_plain%`, `_active_type%`, `_active_level%`, `_active_rarity%`, `_active_role%`,
`_active_bond%`, `_active_fullness%`, `_active_age%`, `_has_active%`, `%boxpet_collection_score%`,
`%boxpet_storage_used%`, `%boxpet_storage_max%`. BoxCore 1.6.0: `%boxcore_bonus_<type>%` (phần cộng thêm của pet).

## API cho plugin khác

`BoxPetService` (ServicesManager): `getActivePet`, `getPets`, `addXp`, `feed`, `bonusTenths`.
Sự kiện: `PetSummonEvent` (huỷ được), `PetDismissEvent`, `PetHatchEvent`, `PetLevelUpEvent`, `PetEvolveEvent`,
`PetAbilityDamageEvent` (huỷ được, sửa sát thương). Gói `com.dex.boxpet.api`, nằm sẵn trong jar.

## Cần kiểm tra trên server thử

Không có server thử trên máy build, nên các điểm sau phải xem tận mắt:

1. Bấm chuột phải vào pet mở menu chăm sóc; Shift + chuột phải ẵm pet (hitbox của dummy ModelEngine).
2. Đứng sau pet của người khác vẫn đánh trúng họ; khi giao tranh hitbox pet tắt.
3. Ẵm pet nhìn ổn ở góc nhìn thứ nhất (chỉnh `carry.up` / `carry.forward` nếu che tâm).
4. Họ nào bay (`follow.fly` trong file pet): mặc định Rồng Lửa và Sư Điểu bay, còn lại đi bộ.
5. Hướng và tốc độ của VFX (cầu lửa, vết chém, đánh dấu) nhìn đúng.
6. Sát thương pet tính là của chủ: điểm hạ gục, BoxClans, top sát thương MyBoxMobs.
7. Pet kinh tế: `/boxcore check <người>` hiện dòng "Cộng thêm (pet)".
