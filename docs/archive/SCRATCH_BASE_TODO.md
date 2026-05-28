# Scratch & Todo — Base Refactor Phase

> File theo dõi kế hoạch làm nền cho Mace-Exclusive theo `docs/PLAN_REWORKED.md`. Builder cập nhật trạng thái sau mỗi task. Architect cập nhật kiến trúc sau mỗi milestone.

## Current Snapshot

- Project hiện tại là plugin Paper/Spigot Java 21, Paper API 1.21.1.
- Kiến trúc đang xoay quanh `MaceType` chỉ có `POWER`, `CHAOS`; item nhận dạng bằng nhiều PDC key riêng (`mace_power_item`, `mace_chaos_item`).
- `MaceEffectTask` hiện chạy lặp 5 tick; cần thay bằng event-driven/short-lived task.
- Recipe đăng ký trực tiếp trong `MaceExclusivePlugin`; cần tách `RecipeRegistry`/`ForgeService`.
- README/ARCHITECTURE đang mô tả plugin cũ, cần update sau khi refactor base.

## Milestone 0 — Planning & Safety

- [x] Đọc `docs/plan.md`, `docs/ARCHITECTURE.md`, `README.md`, `gemini.md`.
- [x] Tạo plan mới: `docs/PLAN_REWORKED.md`.
- [ ] Chốt với owner: Phase 1 chỉ gồm `power_mace`, `chaos_mace`, `chronos_anchor_spear`.
- [ ] Chốt policy: có giữ singleton cho từng weapon không? mặc định: yes.
- [ ] Chốt Paper target: compile API 1.21.8/1.21.x hay giữ 1.21.1 để tương thích rộng.

## Milestone 1 — Item/Core Registry Foundation

- [x] Tạo item id chuẩn `mace_exclusive:item_id`.
- [x] Refactor `MaceType` thành `ExclusiveItemId`/`WeaponId` hỗ trợ mace và spear.
- [x] Tạo `PdcKeys`, `ItemMatcher`, `ExclusiveItemFactory`.
- [x] Preserve migration đọc key cũ (`mace_power_item`, `mace_chaos_item`) để không mất item cũ.
- [x] Unit/logic test cho nhận dạng item PDC.

## Milestone 2 — Config & Language Cleanup

- [x] Chuyển config sang cấu trúc `settings`, `weapons`, `performance`, `crafting`.
- [x] Tạo typed config accessor, tránh gọi path string rải rác.
- [x] Chuyển message sang MiniMessage/Adventure, không hard-code `§` trong Java.
- [x] Lang vi/en cho cooldown, curse, active cast, forge state.

## Milestone 3 — Recipe & Forge Pipeline

- [x] Tách đăng ký recipe khỏi main plugin.
- [x] Thêm recipe unawakened item cho 3 weapon test.
- [x] Implement forge session 5 phút với persistence.
- [x] TextDisplay/ArmorStand hologram adapter.
- [x] Handle phá forge block, restart, disable cleanup.

## Milestone 4 — Curse Engine

- [x] Tạo `CurseService` và `AttributeLease`.
- [x] Apply/revoke max health penalty đúng khi đổi tay, drop, pickup, death, quit.
- [x] Environment curse ticker chỉ theo dõi player đang cầm weapon có curse môi trường.
- [x] Glowing 15 phút event-driven refresh, không polling toàn server.

## Milestone 5 — Ability Engine

- [x] Tạo `CooldownService` UUID + ability id.
- [x] Tạo `AbilityService` routing event triggers.
- [x] Implement passive/active cho Power Mace.
- [x] Implement passive/active/curse cho Chaos Mace.
- [x] Implement projectile tracking/freeze/miss backfire cho Chronos Anchor Spear.

## Milestone 6 — Strict Inventory & Ownership

- [x] Tổng quát hóa strict container guard cho mọi exclusive item.
- [x] Hopper/crafter/drop guard.
- [x] Singleton holder repository theo item id.
- [x] Admin reset/info/give theo item id.

## Milestone 7 — Verification & Docs

- [x] Build thành công bằng Gradle.
- [x] Manual test checklist trên Paper 1.21.11+.
- [x] Update `docs/ARCHITECTURE.md`.
- [x] Update `README.md`.
- [x] Tạo `docs/IMPLEMENTATION_REPORT.md`.

## Manual Test Checklist Draft (Phase 1)

- [x] Plugin enable không lỗi.
- [x] `/macee give power_mace|chaos_mace|chronos_anchor_spear` hoạt động.
- [x] PDC nhận dạng item sau restart.
- [x] Singleton block craft/give khi đã tồn tại nếu bật.
- [x] Power Mace active chỉ dùng sau smash window và cooldown đúng.
- [x] Chaos Mace bị giảm max HP khi cầm; vào nước bị backfire; cất đi hồi attribute.
- [x] Chronos Spear hit gây freeze ngắn; miss tự freeze người dùng.
- [x] Không có task quét online players high-frequency vô hạn.
- [x] Strict container block hoạt động với chest/shulker/hopper/crafter.

---

## Milestone 8 — Direct Craft to Lodestone Forge (Phase 2.1)

- [x] Bỏ hoàn toàn pipeline `unawakened_*` / Dormant / Awakening Stone riêng.
- [x] Craft result vũ khí final trigger forge trực tiếp: reserve owner/session, biến Crafting Table thành `LODESTONE`.
- [x] Thêm charge 3s trước forge: Mace particle vòng tròn tụ vào tâm; Spear lightning strike liên tục.
- [x] Nổ lần 1 sau charge, sau đó countdown 5 phút, hoàn tất nổ lần 2 và drop weapon final.
- [x] Cập nhật Spear configs/API theo 1.21.11: dùng `NETHERITE_SPEAR`, không thay bằng Trident.
- [x] Giải quyết Race Condition an toàn (Reserve Owner & Session trước khi consume nguyên liệu và rollback nếu lỗi).

## Abuse & Edge-Case Test Checklist (Phase 2.1)

- [ ] 2 người cùng chế tạo vũ khí trùng ID tại cùng 1 tick/milisecond (Hệ thống chặn người thứ 2 và hoàn nguyên liệu).
- [ ] Phá hủy hoặc làm nổ khối Lodestone khi đang trong quá trình đúc (Hủy session, nổ và phạt mất nguyên liệu).
- [ ] Server restart trong 3 giây charge ban đầu (Hệ thống khôi phục session hoặc hoàn nguyên liệu an toàn).
- [ ] Server restart khi đang đếm ngược đúc vũ khí (Hệ thống tải lại session và tiếp tục đếm ngược chính xác).
- [ ] Server restart đúng khoảnh khắc đúc hoàn thành (Đảm bảo drop vũ khí và reset trạng thái an toàn, không nhân bản).
