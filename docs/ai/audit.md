# Architecture Audit

Load this for code review, architecture audit, or “check the codebase” requests. Report violations only unless explicitly asked to fix them.

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

End with total violations by category and a short list of compliant areas.
