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

Windows PowerShell build command:

```powershell
if (Test-Path -LiteralPath ".\\gradlew.bat") { .\\gradlew.bat build } else { gradle build }
```

When using shell tools, set `timeout: 120000`.

## Documentation-first workflow

Before production code changes, read:

1. `docs/plan.html`
2. `docs/implementation_tickets.html`
3. `docs/scratch_base_todo.html`
4. `docs/implementation_report.html`
5. `docs/agent_phase3_prompt.md`

Archive docs under `docs/archive/` are historical references only.

## Phase 3 scope

- Mace-first only.
- Do not implement or re-enable spear gameplay until the Architect starts Phase 4.
- Active Mace ability input is Sneak + Left Click only.
- Build must pass before reporting implementation complete.
