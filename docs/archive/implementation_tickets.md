# Implementation Tickets

> Tài liệu chứa các vé triển khai kỹ thuật được bóc tách từ kế hoạch thiết kế của Mace-Exclusive.

---

## Ticket 1

[Target]
Core item identity, registry, and config loading.

[Goal]
Tạo nền để đọc `items/*.yml`, nhận diện item bằng PDC, tạo item từ registry.

[Files]
- `src/main/java/vn/nirussv/maceexclusive/item/*`
- `src/main/java/vn/nirussv/maceexclusive/registry/*`
- `src/main/java/vn/nirussv/maceexclusive/config/*`
- `src/main/resources/items/*.yml`

[Constraints]
- Không so sánh lore/display name để xác định item.
- PDC key chuẩn: `mace_exclusive:item_id`.
- Config thiếu field phải có default an toàn.

[Definition of Done]
- `/macee give <id>` tạo đúng item, đúng CMD, đúng lore.
- Item reload config không mất nhận diện.

[Risks]
- Legacy item key khác format.
- Config YAML sai gây crash plugin.

[Tests to add]
- Unit test parse weapon config.
- Integration test item PDC matcher.

---

## Ticket 2

[Target]
Core crafting and ritual core system.

[Goal]
Implement Crafted Core, Ruined Core restore, Blood/Sculk/Chrono rituals.

[Files]
- `recipe/CoreRecipeRegistrar.java`
- `forge/CoreCraftService.java`
- `listener/CoreRitualListener.java`
- `curse/CoreCurseService.java`

[Constraints]
- Crafted core fail chance config-driven.
- Lockout tính theo online time nếu có sẵn scheduler theo player, không poll toàn server.
- Ritual phải verify block/location cụ thể.

[Definition of Done]
- Craft core thành công/fail đúng.
- Ruined Core restore 100%.
- 3 ritual core tạo item đúng và broadcast đúng.

[Risks]
- Player disconnect trong lúc freeze craft.
- Warden death attribution gần altar.

[Tests to add]
- Mock craft success/fail.
- Ritual location validation tests.

---

## Ticket 3

[Target]
Awakening pipeline.

[Goal]
Unawakened weapon craft -> Redstone Block session -> countdown -> explosion -> final weapon drop.

[Files]
- `forge/AwakeningSession.java`
- `forge/ForgeService.java`
- `persistence/ForgeSessionStore.java`
- `listener/AwakeningCraftListener.java`

[Constraints]
- Session persistent qua restart.
- Không duplicate drop nếu server restart đúng lúc nổ.
- TextDisplay cleanup khi cancel/complete.

[Definition of Done]
- 5 phút đúc hoạt động.
- Phá redstone block hủy và nổ.
- Hoàn thành drop đúng final weapon.

[Risks]
- Chunk unload khi session đang chạy.
- Explosion phá block ngoài ý muốn nếu config không chặn.

[Tests to add]
- Session serialization.
- Cancel/complete idempotency.

---

## Ticket 4

[Target]
Ability and cooldown engine.

[Goal]
Router active/passive cho Mace/Spear, cooldown theo UUID + ability id.

[Files]
- `ability/AbilityService.java`
- `ability/CooldownService.java`
- `ability/mace/*`
- `ability/spear/*`
- `combat/SmashTracker.java`
- `projectile/TrackedSpearService.java`

[Constraints]
- Mỗi weapon chỉ 1 active.
- Passive có cooldown nội bộ nếu có RNG/trigger mạnh.
- Không freeze player bằng NMS; dùng event cancellation/service có TTL.

[Definition of Done]
- Right-Click active gọi đúng skill.
- Smash window 0.8-1.5s hoạt động.
- Spear hit/miss phân biệt đúng.

[Risks]
- Conflict với vanilla right-click mace/trident.
- Projectile metadata mất khi chunk unload.

[Tests to add]
- Cooldown unit tests.
- Smash trigger timing tests.
- Spear hit/miss tests.

---

## Ticket 5

[Target]
Curse and attribute lease engine.

[Goal]
Áp/revoke curse khi cầm, đổi tay, drop, chết, quit, inventory click.

[Files]
- `curse/CurseService.java`
- `curse/AttributeLease.java`
- `listener/CurseStateListener.java`

[Constraints]
- Không stack max HP modifier.
- Curse Glowing dùng potion auto-decay 900s, không xóa ngay khi cất.
- Attribute phải revoke sạch khi không còn điều kiện cầm.

[Definition of Done]
- Cầm/đổi slot/drop không bị stack attribute.
- Death/quit/rejoin không mất máu max vĩnh viễn do bug.

[Risks]
- Nhiều weapon cùng lúc trong main/offhand.
- Plugin reload giữa lúc lease active.

[Tests to add]
- Attribute lease idempotency.
- Main hand/offhand priority tests.

---

## Ticket 6

[Target]
Particle and sound profile system.

[Goal]
Implement reusable primitives: RingBurst, SpiralTrail, BeamLine, GroundCrack, OrbitHalo, ImpactNova.

[Files]
- `effect/ParticleProfile.java`
- `effect/ParticleService.java`
- `effect/SoundProfile.java`
- `effect/ZoneVisualService.java`

[Constraints]
- Particle count cap từ config.
- Active zones có TTL và cleanup.
- Không spawn particle cho player quá xa nếu không cần.

[Definition of Done]
- Mỗi weapon active có visual dễ nhìn.
- Không gây TPS drop khi 5-10 người dùng skill cùng lúc.

[Risks]
- Particle quá nhiều ở combat đông.
- Client FPS drop.

[Tests to add]
- Particle budget tests.
- TTL cleanup tests.

---

## Ticket 3: Forge Pipeline (Updated in Phase 2.1)

[Target]
Hệ thống đúc và thức tỉnh vũ khí trực tiếp.

[Goal]
Thiết lập luồng chốt: Player lấy result craft vũ khí -> atomic reserve owner/session -> bàn chế tạo biến thành Lodestone -> charge particle 3s theo loại vũ khí -> nổ lần 1 -> đếm ngược 5 phút -> nổ lần 2 -> drop vũ khí final. Không còn item unawakened/dormant và không dùng Redstone Block.

[Files]
- `forge/ForgeService.java`
- `forge/ForgeListener.java`
- `forge/ForgeVisualService.java`
- `persistence/ForgeSessionStore.java`

[Constraints]
- Phiên đúc phải được lưu trữ kiên định qua restart/reload.
- Mọi forge block là `LODESTONE`.
- Minecraft 1.21.11 target, Spear dùng đúng `NETHERITE_SPEAR`.

[Definition of Done]
- Lấy result craft biến bàn chế tạo thành Lodestone và reserve owner/session.
- Mace charge 3s tụ hạt tròn vào tâm rồi nổ.
- Spear charge 3s sét đánh liên tục rồi nổ.
- Đúc 5 phút xong nổ lần 2 và drop vũ khí final.

---

## Ticket 7: Atomic Craft Reservation & Abuse Tests (Phase 2.1)

[Target]
Giao dịch Craft nguyên liệu nguyên tử & Ngăn ngừa lỗi khai thác (Race Conditions).

[Goal]
Chặn đứng việc nhân bản vật phẩm hoặc đúc trùng lặp do 2 người chơi cùng nhấn click trong cùng một tick/miligiây, đồng thời bổ sung các cơ chế khôi phục khối và nguyên liệu an toàn khi có sự cố.

[Files]
- `forge/ForgeService.java`
- `listener/MaceListener.java`

[Constraints]
- Toàn bộ thao tác kiểm tra, đặt chỗ (reserve) ID vật phẩm, biến khối và khởi tạo session phải chạy trước khi tiêu thụ nguyên liệu.
- Nếu có bất kỳ lỗi nào trong quá trình khởi tạo hoặc commit phiên đúc, hệ thống phải tự động hoàn lại trạng thái gốc của hòm đồ và khối để tránh mất mát.

[Definition of Done]
- Hai người chế cùng lúc 1 món vũ khí độc quyền sẽ có 1 người thành công và 1 người bị hủy/hoàn nguyên liệu.
- Phá Lodestone giữa chừng hủy đúc và nổ phạt.
- Rollback khối Lodestone về Crafting Table và trả nguyên liệu hòm đồ nếu commit session hoặc nạp hologram thất bại.
