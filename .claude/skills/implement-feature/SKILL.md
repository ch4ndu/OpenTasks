---
name: implement-feature
description: Use when implementing a new feature, screen, or significant change in the OpenTasks KMP app. Guides through proper architecture patterns, platform considerations, and verification.
argument-hint: [feature description]
allowed-tools: Read, Write, Edit, Grep, Glob, Bash, Agent
---

# Implement Feature

Read `AGENTS.md`, then load `docs/ai/feature-implementation.md`.

Also load focused docs as needed:

- UI: `docs/ai/ui.md`
- Data, sync, timestamps, migrations: `docs/ai/data-sync-time.md`
- Widgets: `docs/ai/widgets.md`
- Architecture: `docs/ai/architecture.md`

Do not duplicate durable project rules in this Claude skill. Update the relevant `docs/ai/*` file instead.

## Feature Request

$ARGUMENTS
