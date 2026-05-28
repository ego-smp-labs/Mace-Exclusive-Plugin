# Implementation Report: Base Refactor Phase

## Overview
This report details the successful implementation of the "Base Refactor Phase" for Mace-Exclusive, targeting Paper/Minecraft 1.21.11+ using Java 21. 
The objective was to transition from a prototype singleton-mace plugin to a robust, event-driven, vanilla-plus exclusive weapon framework supporting custom items, curses, abilities, projectiles, and an immersive awakening (forge) pipeline.

## Architectural Improvements

### 1. Item & Registry Engine
- **Unified PDC Key**: Replaced scattered tags (`mace_power_item`, `mace_chaos_item`) with a single root `mace_exclusive:item_id` and an optional `owner` key.
- **Migration & Fallback**: Successfully preserved the ability to detect previously existing items via `ItemMatcher`.
- **Dynamic Factory**: `ExclusiveItemFactory` generates generic and custom-model weapons dynamically based on `WeaponConfig`.

### 2. Config & Localization
- **Separation of Concerns**: Moved from a monolithic `config.yml` to a structured config (`settings`, `weapons`, `performance`, `crafting`) and externalized translations into `lang_vi.yml` and `lang_en.yml`.
- **Adventure API Integration**: Completely removed legacy `§` parsing from Java logic. Component-based translation guarantees cleaner code and better text formatting.

### 3. Curse Engine
- **AttributeLease**: A system engineered to apply and cleanly revoke AttributeModifiers (specifically Max Health). It prevents health stacking or leakage across death, item drop, quit, and dimension changes.
- **Environment Backfire**: Water damage for the Chaos Mace is tracked in real-time but heavily optimized. It only evaluates players who are actively holding the cursed weapon instead of scheduling global server ticks.

### 4. Ability Engine & Cooldowns
- **CooldownService**: Tracks skill cooldowns via `UUID + ability_id`.
- **Active & Passive Routing**: Abstract interfaces (`ActiveAbility`, `PassiveAbility`) triggered by an internal event pipeline (`AbilityService`).
- **Effect Profiles**: Custom `ParticleProfile` and `SoundProfile` classes keep ability implementations focused purely on logic.

### 5. Projectile System (Chronos Anchor Spear)
- **Tracking**: Hooks into the Trident launch mechanism by attaching PDC metadata directly to the spawned entity.
- **Freeze Service**: Cancels player movement, jumping, and interactions without interfering with chat or admin commands.
- **Miss Backfire**: If the spear hits a block or misses an entity, the weapon is gracefully returned while applying a localized freeze penalty to the user.

### 6. Forge / Awakening Pipeline
- **Session Lifecycle**: `ForgeService` monitors the 5-minute awakening process on an authorized forge block.
- **Visuals**: Uses `TextDisplay` entities for modern, clean countdown holograms (with `ArmorStand` fallback).
- **Persistence**: Sessions are safely written to `forge-sessions.yml` to survive server reloads and restarts.

### 7. Strict Inventory & Ownership Guard
- **Singleton Model**: `MaceRepository` ensures only one instance of an exclusive weapon per server/world exists, if enabled.
- **Container Blocking**: Prohibits exclusive items from entering Chests, Shulker Boxes, Hoppers, Dispensers, Droppers, and the Crafter block.
- **Dupe Prevention**: Hard blocks on GUI click-drags, automated hopper intake, and entity extraction.

## Current Supported Weapons
1. **Power Mace**: Active Ground Pulse, Passive Stored Momentum.
2. **Chaos Mace**: Active Rift Reversal, Passive Fractured Step, Max Health Curse, Water Backfire Curse.
3. **Chronos Anchor Spear**: Projectile tracking, Hit Freeze, Miss Backfire.

---

## Phase 2.1 Update: Direct Craft to Lodestone Forge

### 1. Reverted Awakening Stone / Dormant Weapons
- Removed the two-step awakening mechanism with dormant weapons and awakening stones.
- The crafting recipes now target the final weapons directly. Crafting them triggers the forge pipeline immediately on the crafting table block.

### 2. Direct Forge Visuals & Lifecycle
- Upon crafting, the crafting table is transformed into a **Lodestone** block.
- **Pre-Forge Charging (3s)**: Play visual effects before starting the main 5-minute countdown.
  - **Mace**: Concentric circle particle effects merging into the center.
  - **Spear**: Continuous lightning strikes striking down on the Lodestone.
- **Stage 1 Explosion**: A minor explosion occurs at the end of the 3-second charge, and the 5-minute countdown begins (rendered via TextDisplay hologram).
- **Stage 2 Explosion**: Once the 5 minutes expire, a second explosion triggers, dropping the final weapon in invulnerable state (with pickup priority for the crafter).

### 3. Transaction Safety (Anti-Race Condition)
- Implemented strict state locking (`reservedItemIds`) inside `ForgeService`.
- If two players attempt to craft the same exclusive item at the same tick/millisecond, the second craft will be rejected.
- Checked and reserved all IDs/session states *before* consuming inventory crafting items.
- If hologram creation or session commit fails, the Lodestone block is restored to a crafting table and inventory materials are safely rolled back, preventing item loss or dupe exploits.

### 4. Minecraft 1.21.11 & Netherite Spear Compatibility
- Updated Trident fallback settings for `chronos_anchor_spear.yml`. It now natively resolves to the official custom item `NETHERITE_SPEAR` on 1.21.11.

## Conclusion
The project has successfully completed the Phase 2.1 refactor. It ensures transactional safety for high-concurrency/race-condition abuse vectors and delivers an immersive, vanilla-like forge visual experience.
