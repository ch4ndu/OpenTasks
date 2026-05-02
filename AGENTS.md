# OpenTasks Agent Instructions

OpenTasks is a Kotlin Multiplatform Compose task-management app with Eisenhower Matrix prioritization, Room persistence, Koin DI, PocketBase sync, and Android Glance widgets.

Read this file first. Do not eagerly load every AI instruction document. Load only the files below that match the task.

## Load Only When Needed

| Task | Load |
| --- | --- |
| Architecture, source layout, data flow | `docs/ai/architecture.md` |
| Feature implementation, bug fixes, refactors | `docs/ai/feature-implementation.md` |
| Code review or architecture audit | `docs/ai/audit.md` |
| UI, Compose, previews, recomposition | `docs/ai/ui.md` |
| Room, repositories, sync, timestamps, date/time | `docs/ai/data-sync-time.md` |
| Android Glance widgets | `docs/ai/widgets.md` |

## Always-On Rules

- Read existing code before editing; follow local patterns over inventing new ones.
- Prefer `commonMain`; use platform code only when necessary.
- Preserve the project layering: data repositories, domain UseCases/Actions, ViewModels, then UI.
- ViewModels use UseCases and Actions, never repositories directly.
- Never use Kotlin `!!`; use safe calls, early returns, defaults, or smart-cast locals.
- Reuse existing UseCases, Actions, composables, utilities, strings, icons, and theme dimensions before creating new ones.
- Keep changes scoped. Do not refactor unrelated code while implementing a feature or fix.
- When durable architecture, workflow, or verification rules change, update the relevant `docs/ai/*` file. Do not duplicate those rules in tool-specific shims.
