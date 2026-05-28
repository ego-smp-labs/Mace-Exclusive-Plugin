# Prompt giao việc cho opencode-cli Backend Agent — Phase 3 Mace-First

Role: Senior Java Paper/Spigot Developer.

Build rule bắt buộc (KHÔNG ĐƯỢC QUÊN):
- Mọi lệnh build/test phải có timeout <= 120s.
- Với tool shell/bash phải set `timeout: 120000`.
- Lệnh Windows chuẩn: `if (Test-Path -LiteralPath ".\\gradlew.bat") { .\\gradlew.bat build } else { gradle build }`.
- Tối đa 3 lần build trong một lượt.
- Nếu timeout/fail, dừng và trả log.
- Không chạy build/test loop vô hạn, không để agent treo.

Đọc trước khi code:
1. `docs/plan.html`
2. `docs/implementation_tickets.html`
3. `docs/scratch_base_todo.html`
4. `docs/implementation_report.html`
5. Nếu cần tham chiếu lịch sử: `docs/archive/*`

Mục tiêu Phase 3:
- Mace-first. Không triển khai gameplay spear trong phase này.
- Disable spear listener/service registration nếu đang bật.
- Active ability của mọi mace = Sneak + Left Click. Right Click không active.
- Chrono Core đổi thành End Core (`end_core`). Không còn `chrono_core` trong code/resource active.
- Bỏ Devourer/Phoenix/Aegis.
- Gộp Gravity + Singularity thành `gravity_mace`.
- Cập nhật recipes/lore/particles/curses theo `docs/plan.html`.
- Đảm bảo OOP/SOLID: listener mỏng, logic trong service/ability, particle qua service/profile.

Implementation order đề xuất:
1. Scope cleanup:
   - Grep và loại bỏ/disable `chrono_core`, `dark_ego`, removed maces, spear gameplay registration.
   - Không hồi sinh `MaceType`, `MaceFactory`, `ExclusiveItemId`, `egosmp`.
2. Config/resource sync:
   - Update `cores/*.yml`, `items/*.yml` cho 7 mace: power, void, chaos, vampiric, gravity, sonic, soulfire.
   - Lore mỗi item ghi ngắn active/passive/curse/cooldown.
3. Core/special materials:
   - End Core ritual.
   - No active Chaos Core recipe/requirement; Chaos Mace direct recipe uses exact items: void_mace + blood_core + wither_rose + 6 obsidian_chaos.
   - Obsidian Chaos creeper 5% drop.
   - Challenger's Eye from Enderman Totem proc.
4. Ability input:
   - Sneak + LeftClick air/block/entity.
   - If active cast succeeds on entity hit, cancel normal damage.
   - If cooldown/condition fail, allow normal hit.
5. Mace mechanics:
   - Chaos full rage/effect/backfire/water curse.
   - Void devour passive/resurrection/mind detachment/balancing curse.
   - Vampiric/Power/Gravity/Sonic/Soulfire per plan.
6. Particle/sound coverage:
   - Every mace gets hold/active/hit/backfire visual where relevant.
   - Respect particle budget and TTL cleanup.
7. Tests/verification:
   - Grep: `MaceType|MaceFactory|ExclusiveItemId|egosmp|dark_ego|chrono_core|devourer_mace|phoenix_mace|aegis_mace` no active matches.
   - Build pass timeout 120s.
   - Manual QA checklist returned.

Definition of Done:
- `gradle build` passes within 120s.
- Spear gameplay disabled for Phase 3.
- All 7 mace configs/lore/recipes match plan.
- Active trigger is Sneak+LeftClick only.
- Particle/sound described in plan is implemented or ticketed explicitly if deferred.
- Reviewer agent receives a summary with files changed, build output, and remaining risks.

# Prompt cho Reviewer Agent

Role: Senior Code Reviewer. Không sửa file.

Review after backend implementation:
- Verify docs/code sync with `docs/plan.html`.
- Check removed/renamed tokens.
- Check active trigger: no RightClick active.
- Check spear gameplay disabled.
- Check chaos hotbar shuffle cannot lose items and restores slots.
- Check void resurrection does not conflict with Totem and respects cooldown.
- Check Forge race tests still valid.
- Check particle tasks have TTL and no leaks.
- Build only if needed, timeout 120s.

Output:
- BLOCKER bugs with file/logic.
- HIGH/MEDIUM risks.
- QA checklist.
- Suggested backend tickets.
