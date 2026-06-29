# Mace-Exclusive Documentation

This docs folder is the active project planning and implementation hub for Mace-Exclusive.

## Current source of truth

1. [`plan.html`](plan.html) — active planning wiki: gameplay ideas, architecture plans, recipes under design, status tags (`Idea`, `Planned`, `Done`).
2. [`implementation_tickets.html`](implementation_tickets.html) — implementation-ready tickets for backend/reviewer agents.
3. [`scratch_base_todo.html`](scratch_base_todo.html) — active checklist for Phase 4.
4. [`implementation_report.html`](implementation_report.html) — current implementation snapshot and known gaps.
5. [`ticket_template.html`](ticket_template.html) — template for new implementation tickets.
6. [`../wiki.html`](../wiki.html) — released/done gameplay documentation for users, developers, and agents.
7. [`../AGENTS.md`](../AGENTS.md) — repository-wide operating rules, including mandatory build/test timeouts.

## Archive

Historical files under archive/task folders are references only. Do not treat archived files as active requirements unless the Architect copies them into `plan.html` first.

## Current phase

Phase 4 is **Overhaul Cleanup + Core/Weapon Planning**:

- The active docs are synchronized as: `plan.html` = design, `implementation_tickets.html` = build-ready tickets, `scratch_base_todo.html` = checklist.
- P0 fixes are implemented: particle safety, Sneak+Left-Click target detection, grave/hopper compatibility.
- Spear gameplay is active; do not disable it unless explicitly requested.
- Ritual Core branch, 6 Weapon Systems, Void Edge redesign, Glitch Clock rules, and center-core recipes are recorded in active docs.
- Released/done gameplay belongs in root `wiki.html`; planned mechanics must stay clearly marked.
- Build command on Windows: `cmd /c "gradle build --no-daemon"` with timeout `120000ms`.
