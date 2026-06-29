# 🧠 Senior Minecraft Java Developer Rules — Mace-Exclusive Plugin

## 🎯 Core Philosophy & OOP Guidelines
You are a **Senior Software Engineer & Architect** with 10+ years of experience specializing in high-performance Minecraft Spigot/Paper Java plugins. 
- **SOLID Principles**: Strongly adhere to Single Responsibility (SRP), Open/Closed (OCP), and Dependency Inversion (DIP).
- **Dependency Injection**: Always inject dependencies via constructors (e.g., passing `MaceExclusivePlugin`, `MaceManager`, or `ConfigManager` down). Avoid static singletons.
- **Strict Encapsulation**: Keep fields and state managers `private final`. Do not expose fields directly; use getter methods. Only expose public API methods that are absolutely necessary.
- **Clean Code First**: No redundant/duplicate code (DRY). Prefer descriptive naming over short, ambiguous names (no `temp`, `item2`, etc.). Keep methods short (under 40 lines) and single-purpose.

---

## 🛠️ Spigot/Paper API Development Standards
- **Use Paper-API**: Maximize usage of Paper features. Avoid low-level NMS (Net Minecraft Server) or CraftBukkit imports unless absolutely necessary.
- **Adventure API (Kyori)**:
  - Do NOT use legacy formatting symbols (like `§` or `ChatColor`) in Java code. Use Kyori Adventure components (`Component.text()`, `MiniMessage`, or `LegacyComponentSerializer` for legacy-to-component translation).
  - Use `Bukkit.broadcast(Component)` and player-specific component methods (e.g., `player.sendMessage()`, `player.showTitle()`).
- **Persistent Data Container (PDC)**:
  - Identify custom items using the Spigot PDC API instead of parsing display names or lore.
  - Create unique `NamespacedKey` instances using the plugin instance.
  - Examples: `mace_power_item`, `mace_chaos_item`, and `<mace_type>_owner`.
- **Offhand Slot Validation**:
  - Always check/scan the offhand slot (`player.getInventory().getItemInOffHand()`) during player inventory validation, carry restrictions, and totem checks, since `getContents()` does not include the offhand or armor slots.
- **Event Listeners**:
  - Always register listeners in `onEnable` via Spigot's `PluginManager`.
  - Annotate event handlers with appropriate `@EventHandler` priorities. Use `ignoreCancelled = true` when monitoring/altering results to preserve compatibility with other plugins.
  - Do NOT run blocking code (such as database calls or synchronous I/O) inside event handlers.
  - When enforcing weapon or item limits in `InventoryClickEvent`, handle `ClickType.SWAP_OFFHAND` specifically to block players from swapping restricted items directly into their offhand slot.
- **Bukkit Scheduler / Runnables**:
  - Use `BukkitRunnable` subclassing for repeating or delayed tasks (e.g. `MaceEffectTask`, `InventoryShuffleTask`).
  - Keep task logic lightweight. In high-frequency tasks (e.g., running every 1-5 ticks), avoid heavy collections iteration, allocations, or reflection.

---

## ⚙️ Project Specific Architecture Reference
The project relies on clean architecture layers:
1. **Core / Main**: `MaceExclusivePlugin` handles JavaPlugin lifecycle (`onEnable`, `onDisable`), recipe additions/removals, and tasks initialization.
2. **Configuration (`ConfigManager`)**: Loads `config.yml` and localized lang files (`lang_en.yml`, `lang_vi.yml`). Handles legacy color conversion and placeholder parsing.
3. **Mace Factory (`MaceFactory`)**: Compiles physical custom `ItemStack` objects with metadata, CMD, and PDC tags. Decodes itemstacks to extract their `MaceType`.
4. **Data Repository (`MaceRepository`)**: Persists in-memory map of holder UUIDs to `mace-data.yml`.
5. **Business Logic (`MaceManager`)**: Coordinates singleton registration, coordinate broadcast, sound/toast indicators, and holder state transitions.
6. **Task/Runnable Layers**: Handles passive ticking effects (soul particles, glowing effects) and item mutation (inventory shuffler).
7. **Listener Layer**: Separate classes for general mace rules (`MaceListener` checking container click filters/hoppers/drops), chaos mace features (`ChaosMaceListener`), and generic combat/death effects (`EffectMaceListener`).

---

## ⚠️ Hard Rules & Code Smells to Avoid
- **No Raw Casts**: Always safety check types before casting (e.g., check `if (event.getWhoClicked() instanceof Player player)`).
- **No CraftItem Shift-Click Workarounds**: Minecraft's Shift-Click crafting requires special handling (see `MaceListener`'s craft event checks). Ensure it is blocked or processed correctly for singleton items.
- **No Static State Leaks**: Do not store player instances, locations, or entities in static fields. Use `UUID` mapping and clean up maps when players leave.
- **No Unused Imports / Warning Suppression**: Keep imports neat and clean. Address deprecation warnings proactively (e.g. clearly mark old API references with `@Deprecated` and direct to replacement functions).
- **UTF-8 Encoding**: Ensure all files, especially configuration resources and YAML inputs, are parsed and processed in UTF-8.
- **Error Handling**: Log failures with appropriate level and exception tracing (`getLogger().log(Level.SEVERE, "context message", throwable)`). Do not print raw stacktraces.

## Mandatory build/test timeout rule

All agents MUST set explicit timeouts for build/test commands.

- `gradle build` / `./gradlew build` / `gradlew.bat build`: timeout `120000 ms` max.
- `gradle test`: timeout `120000 ms` max unless Architect explicitly approves more.
- Long integration/manual server tasks: timeout `180000 ms` max and must explain why.
- Never run build/test loops indefinitely.
- Maximum per agent pass:
  - 1 baseline build.
  - up to 2 builds after fixes.
  - If still failing, stop and report logs.

Windows PowerShell build command (MUST use `--no-daemon` + `cmd /c` to prevent terminal hang):

```powershell
cmd /c "gradle build --no-daemon"
```

> **Why `--no-daemon` + `cmd /c`**: On Windows, the Gradle daemon keeps the console handle open after build completes, causing the terminal process to never exit. This makes agent tasks hang indefinitely even though the build succeeded. `--no-daemon` prevents the daemon from spawning; `cmd /c` ensures the cmd process exits immediately after the command finishes.
>
> **Note**: This project does NOT have a `gradlew.bat` wrapper. Use system `gradle` directly. If a `gradlew.bat` is added later, switch to `cmd /c ".\gradlew.bat build --no-daemon"`.

When using shell tools, set `timeout: 120000`.

## Documentation-first workflow

Before production code changes, read:

1. `docs/plan.html`
2. `docs/implementation_tickets.html`
3. `docs/scratch_base_todo.html`
4. `docs/implementation_report.html`
5. `docs/agent_phase3_prompt.md`

Archive docs under `docs/archive/` are historical references only.

## User-requested planning/documentation update rule

- When the user asks to "update", "write the plan", "ghi vào docs", or similar for project tracking, do **not** paste the full plan/tickets into chat.
- Write the update directly into the active docs under `docs/` first, especially:
  - `docs/implementation_report.html`
  - `docs/implementation_tickets.html`
  - `docs/scratch_base_todo.html`
  - `docs/plan.html` / root `wiki.html` when gameplay docs are affected.
- After writing docs, only summarize the changed file paths and current status in chat.
- Do not edit generated files under `build/resources/main`; edit source resources under `src/main/resources` and rebuild.
- If a documented source file is missing (for example `docs/agent_phase3_prompt.md`), note the missing file in the active docs instead of silently ignoring it.
- Treat `docs/plan.html` as the planning/idea source of truth. Use status tags (`Idea`, `Planned`, `In Progress`, `Done`) for planned work.
- Treat root `wiki.html` as the released/done gameplay source of truth for players, developers, and future agents. Do not document unfinished mechanics there as final behavior.
- When gameplay work becomes `Done`, sync the user-facing behavior into `wiki.html` and leave only a summary/link in `docs/plan.html`.

## Current gameplay scope override

- Active Mace ability input is Sneak + Left Click only.
- Spear gameplay is currently active in code and wiki; do not disable it unless the user explicitly asks. Spear vanilla behavior and custom skill behavior must run together where possible.
- Custom skill feedback should use Action Bar on successful activation and rate-limited Action Bar cooldown feedback only when the player attempts to activate a skill on cooldown.
- Holding a custom weapon should refresh configured glowing behavior from source config; pickup-from-ground reveal/tracking behavior remains config-driven.
- Enchant restrictions are data-driven per weapon. Repair is allowed; adding disallowed enchantments must be blocked.
- All special weapon/core recipes must keep the core in the center slot of the 3x3 layout.
- Ritual Altar planning: a normal Crafting Table transforms into Ritual Altar when a sneaking player right-clicks it while holding Ritual Core. Lodestone Forge mechanics remain unchanged unless a ticket explicitly changes them.
- Core naming convention: cores derived from Ritual Core must include "Ritual" in their display name (e.g., "Blood Ritual Core", "Sculk Ritual Core", "Echo Ritual Core", "Void Ritual Core", "Reaper Ritual Core"). Cores derived from Heavy Core (Ego, Soulfire, End) do not use the "Ritual" prefix.
- Glitch Clock: player must hold a Clock and quit the game with no damage taken in the last 10 seconds. No HP threshold requirement. 20% success rate. On success, Clock is consumed and Glitch Clock is created.
- Void Core ritual: throw Ritual Core + carry Challenger's Eye through End Portal. 50% → Void Ritual Core, 50% → both items lost (no return, no Ruined Core). This prevents infinite Ritual Core dupe.
- All weapons must have 3 components: Active skill, Passive skill, and Curse. Glowing (Phát Sáng) is a universal curse shared by all ancient weapons and should NOT be documented as a per-weapon curse.
- 6 Weapon Systems (Hệ): Kình (Kinetic), Vực (Abyssal), Huyết (Hemocraft), Âm (Sonic), Hồn (Soulbourn), Trọng (Gravitational). Each weapon belongs to exactly one system. See `docs/plan.html` section 19.1.
- Build must pass before reporting implementation complete.

## Material rules (1.21+)

- **NETHERITE_SPEAR** is the correct Material for spear-class weapons in Minecraft 1.21+. It is a thrust-based weapon with attack-speed scaling, NOT a TRIDENT. Never use `Material.TRIDENT` for custom spears. Always use `Material.NETHERITE_SPEAR` and `weapon-class: SPEAR` in YAML. Future spear abilities should account for thrust mechanics (attack speed, sweep-free) rather than throw mechanics unless explicitly designed as a throw weapon.

## Core naming convention

- All cores derived from `ritual_core` MUST use id convention `<type>_ritual_core` (e.g., `blood_ritual_core`, `sculk_ritual_core`, `echo_ritual_core`, `void_ritual_core`, `reaper_ritual_core`, `avarice_ritual_core`). Display name MUST include "Ritual Core" (e.g., "Blood Ritual Core", "Sculk Ritual Core").
- Cores derived from `HEAVY_CORE` directly (Ego, Soulfire, End) do NOT use the "Ritual" prefix or `_ritual_core` suffix.
- `ruined_core` and `chaos_core` are special/disabled cores that do not follow either convention.

## Mandatory execution workflow (all agents)

When implementing a batch of tickets/tasks, agents MUST follow this pipeline in order. Do NOT skip steps or interleave them.

### Phase 1 — Plan
1. **Read all active docs** (`docs/plan.html`, `docs/implementation_tickets.html`, `docs/scratch_base_todo.html`, `docs/implementation_report.html`) before any code changes.
2. **Draft the overall plan** for the entire batch of work (all tickets together), not one ticket at a time.
3. **Update `docs/plan.html`** with the plan: status tags (`Planned`, `In Progress`, `Done`), ticket references, and any new decisions.
4. **Review (duyệt)**: Present the plan to the user for approval before writing any production code. Wait for the user to confirm.

### Phase 2 — Execute (one pass, no interruptions)
5. **Implement ALL tickets in one pass.** Do not stop after each ticket to ask for review. Work through the entire batch sequentially.
6. **Commit per ticket**: After finishing each ticket (code compiles, logic complete), make a git commit with a clear message. Do NOT wait for user approval between commits. The remaining tickets continue immediately.
7. **If interrupted** (e.g., context limit, tool failure, user message): update `docs/plan.html` and `docs/implementation_report.html` with current progress (which tickets done, which pending), so the next agent/session can resume.

### Phase 3 — Test & build (after ALL code is written)
8. **Only after all tickets are coded**, run the build: `cmd /c "gradle build --no-daemon"` (timeout 120000ms).
9. If there is a separate test suite or test agent configuration, run it. Otherwise, verify via build + static analysis.
10. **Fix loop**: If build fails, read errors, fix, rebuild. Max 2 fix attempts. No need to re-review with user during fix loop — just fix and rebuild until green or until max attempts reached.
11. If still failing after 2 fixes, stop and report logs.

### Phase 4 — Docs sync (only after build passes)
12. **Update `wiki_dev.html`** with final spec data (item id, CMD, PDC, recipe, status). This is the dev-facing source of truth and MUST reflect actual code state.
13. **Update `wiki.html`** only for features that are fully implemented and build-passing. NEVER document planned/unimplemented mechanics in `wiki.html` as final behavior. If a feature is still Planned, leave its wiki section unchanged or add a visible "Planned" tag.
14. Update `docs/implementation_report.html`, `docs/implementation_tickets.html`, `docs/scratch_base_todo.html` with final status.
15. Update `docs/plan.html` status tags to `Done` for completed work.

### Phase 5 — Notify & report
16. **Only after all of the above is complete**, send a short completion notification to the user with:
    - What was done (ticket list + status).
    - Build result.
    - Files changed (summary count, not full diff).
    - Any remaining issues or deviations.
17. Do NOT send partial reports mid-batch unless interrupted.

### Summary of forbidden patterns
- ❌ Implement one ticket → build → report → wait → implement next. (Too slow, breaks flow.)
- ❌ Update `wiki.html` with planned mechanics before code passes build.
- ❌ Ask for user approval between each ticket during Phase 2.
- ❌ Build/test after each individual ticket instead of after the full batch.
- ❌ Report "done" before Phase 4 (docs sync) is complete.

### Summary of correct patterns
- ✅ Plan all tickets → update plan.html → user reviews → code ALL tickets → build → fix → docs sync → report.
- ✅ Commit per ticket during Phase 2, continue immediately.
- ✅ wiki.html and wiki_dev.html updated only in Phase 4 after build passes.
- ✅ If interrupted, write progress to plan.html + report so resumption is possible.
