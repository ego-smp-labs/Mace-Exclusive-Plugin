# Archived external agent implementation plan — 2026-05-28

Source: `C:\Users\nhatt\.gemini\antigravity-ide\brain\5757b74c-30eb-47a1-9d04-c95bda0f7fe4\implementation_plan.md`

> Historical reference only. Current source of truth is `docs/plan.html`.

## Summary

External agent proposed Phase 3 reconstruction:

- Active abilities use **Sneak + Left Click**.
- Chrono Core renamed to **End Core**.
- Added `obsidian_chaos`, `chaos_core`, `challenger_eye`.
- Proposed mace set: Power, Chaos, Void, Vampiric, Gravity, Sonic, Soulfire.
- Proposed special material acquisition:
  - Creeper explosion while holding Obsidian -> 5% Obsidian Chaos.
  - Enderman fatal damage + Totem proc -> Challenger's Eye.
- Proposed ability classes:
  - `ChaosMaceAbility`
  - `VoidMaceAbility`
  - `VampiricMaceAbility`
  - `GravityMaceAbility`
  - `SonicWardenMaceAbility`
  - `SoulfirePyreMaceAbility`

## Notes from Architect

This plan was partially useful but not authoritative. The current plan was rewritten into `docs/plan.html` to:

- Scope Phase 3 to Mace only.
- Disable Spear gameplay until Phase 4.
- Explicitly document particles/sounds/lore for each Mace.
- Add build timeout and agent coordination rules.
