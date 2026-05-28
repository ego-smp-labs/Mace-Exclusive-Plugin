# Mace-Exclusive Documentation

This docs folder is the active project wiki for Mace-Exclusive.

## Current source of truth

1. [`plan.html`](plan.html) — master wiki: gameplay design, architecture, recipes, skills, curses, particles, QA matrix, roadmap.
2. [`implementation_tickets.html`](implementation_tickets.html) — implementation-ready tickets for backend agents.
3. [`scratch_base_todo.html`](scratch_base_todo.html) — active checklist for Phase 3.
4. [`implementation_report.html`](implementation_report.html) — current implementation snapshot and known gaps.
5. [`agent_phase3_prompt.md`](agent_phase3_prompt.md) — prompt/rules for opencode-cli backend/reviewer agents.
6. [`../AGENTS.md`](../AGENTS.md) — repository-wide operating rules, including mandatory build/test timeouts.

## Archive

[`archive/`](archive/) contains historical references only. Do not treat archived files as active requirements unless the Architect copies them into `plan.html` first.

## Current phase

Phase 3 is **Mace-first**:

- Spear gameplay is disabled until Phase 4.
- Active Mace ability input is Sneak + Left Click.
- Chrono Core is replaced by End Core.
- Devourer/Phoenix/Aegis are out of scope.
- Gravity and Singularity are merged into `gravity_mace`.
