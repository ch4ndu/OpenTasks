# Architecture Audit

Load this for code review, architecture audit, or “check the codebase” requests. Report violations only unless explicitly asked to fix them.

For any UI, Compose, or screen audit, also load `docs/ai/ui.md` and apply its Compose-sensitive rules.

## ViewModel And Domain Checks

- ViewModels are screen-specific and do not own another screen's behavior.
- ViewModels inject UseCases and Actions, not repositories.
- Reads flow through UseCases; writes flow through Actions.
- DI wiring in `AppModule.kt` is complete.
- DB writes use `Dispatchers.IO`.
- Flow transforms use `.flowOn(Dispatchers.Default)` where appropriate.
- UI state flows use `WhileSubscribed(5000)`.

## UI Checks

- One composable represents one UI component.
- Screens and bottom sheets use a Screen/Content split.
- `@Preview` composables live in `androidMain`, not `commonMain`.
- State is collected at the lowest practical scope.
- User-visible text comes from `stringResource()`.
- `dp` values come from `OpenTasksTheme.dimens`, except 0-2dp inline spacing and preview containers.
- Screens account for translucent overlay top and bottom bars.
- `LazyColumn` uses stable keys such as `key = { it.id }`.
- Filtering, sorting, grouping, and mapping are not done in composables.
- Shared patterns are reused or extracted instead of duplicated.

## Compose Recomposition And Performance Checks

Every architecture or code audit must include a Compose recomposition and performance pass. For UI, Compose, or screen-specific audits, load `docs/ai/ui.md` alongside this file and treat its state and performance rules as audit requirements.

- `[FLOW_SCOPE]` Look for state or flow collection that remains active beyond the UI state, mode, or component that needs it.
- `[RECOMPOSITION]` Look for broad state propagation, unstable inputs, or expensive derivation work that can cause avoidable recomposition.
- `[RECOMPOSITION]` Look for screen state that should be projected, indexed, or precomputed before reaching composables.
- `[LAZY_VIRTUALIZATION]` Look for lazy layouts that lose virtualization, stable identity, or efficient row-level recomposition.
- `[RECOMPOSITION]` Treat `remember`, `derivedStateOf`, and similar Compose-local caching as performance tools, not replacements for correctly scoped ViewModel or domain projections.
- Surface any missed Compose performance issue even if it is not named here; this list defines categories, not an exhaustive checklist.

## Data, Sync, And Date/Time Checks

- No `@Immutable` or `@Stable`; strong skipping is enabled.
- No Kotlin `!!`.
- Repositories soft delete; they do not call `dao.delete()` except approved local-clear flows.
- Repository inserts fill default timestamps.
- User-visible DAO queries filter `isDeleted = 0`.
- No raw date literals such as `86400000L`; use date/time utilities or named constants.
- Calendar views do not filter out completed tasks.
- UTC/local conversion stays in repositories.
- Actions use `localNow()`, not `utcNow()`, except approved notification/import boundaries.
- Notification scheduling reads raw UTC through UTC-specific repository methods.
- `WidgetDataProvider` and `SyncService` may read DAOs directly as boundary exceptions.

## Report Format

```text
**[RULE]** - file:line
Violation description.
Fix: what to change.
```

Use visible, searchable labels for Compose performance findings, including `[RECOMPOSITION]`, `[LAZY_VIRTUALIZATION]`, and `[FLOW_SCOPE]`.

End with total violations by category and a short list of compliant areas.
