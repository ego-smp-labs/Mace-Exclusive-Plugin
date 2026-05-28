# Docs Archive

This folder stores historical references only. Agents must not treat archived files as current requirements unless the Architect explicitly says so.

## Current source of truth

Use the root docs folder:

1. `../plan.html` — wiki/master gameplay plan for the current phase.
2. `../implementation_tickets.html` — implementation-ready tickets.
3. `../scratch_base_todo.html` — phase checklist and QA plan.
4. `../implementation_report.html` — current architecture snapshot and known gaps.
5. `../agent_phase3_prompt.md` — prompt/rules for opencode-cli backend/reviewer agents.
6. `../../AGENTS.md` — mandatory operating rules, especially build/test timeouts.

## Archived files

- `IMPLEMENTATION_REPORT.md` — historical base refactor/Phase 2.1 report.
- `implementation_tickets.md` — historical ticket set before Phase 3 reset.
- `SCRATCH_BASE_TODO.md` — historical todo list before Phase 3 reset.
- `antigravity_implementation_plan_2026-05-28.md` — external agent plan snapshot.
- `antigravity_walkthrough_2026-05-28.md` — external agent walkthrough snapshot.

## Policy

- Do not edit archived files for current implementation.
- If an old idea is revived, copy it into root `plan.html` first and mark it as current.
- Root docs must remain synchronized with active code and active implementation scope.
