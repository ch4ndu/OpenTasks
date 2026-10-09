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

For Kotlin/Native framework linking with Kotlin `2.4.21`, use ordinary parameters for private suspend helpers that accept Room entities. Suspend extension receivers on entities with nested `@Index` annotations trigger a compiler failure in coroutine lowering; preserve the same model and side-effect order when converting such helpers.

## Phase 5 dependency and lint policy

Dependency freshness is the normal goal. Update compatible catalog entries in small attributable batches and compile the affected path after each batch. RichEditor `1.2.1` uses Compose Multiplatform `1.12.1` and Material 3 `1.12.0-alpha03`; retain HTML persistence for notes and task descriptions and verify formatting/save/reopen behavior when changing the editor or its parser. JetBrains Markdown is explicitly selected at `0.7.16` for parser fixes. Gradle `9.4.1` and AGP `9.2.1` remain paired, within Kotlin `2.4.21`'s published compatibility range. Compile against stable Android API 37 for the Compose, Core, and Lifecycle dependencies; target SDK remains 36 and minimum SDK remains 26. Keep Navigation 3 runtime `1.1.7` paired with stable JetBrains UI `1.1.2`, and use stable adaptive `1.3.0`. The Ktor BOM aligns PocketBase's transitive runtime clients with the mock engine; coroutines core is declared in commonMain so runtime targets receive the catalog version. Verify resolved production graphs, not just declared catalog versions. Preview dependencies remain pinned only where no compatible stable replacement exists. No broad lint baseline is permitted: source, resource, RTL, performance, accessibility, and dependency-scope warnings must be fixed or narrowly justified.
