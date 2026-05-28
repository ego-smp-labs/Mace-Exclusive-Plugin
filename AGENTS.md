# Agent Operating Rules — Mace-Exclusive

This repository is managed by an Architect agent coordinating backend/reviewer agents.

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
