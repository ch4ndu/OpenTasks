---
name: audit
description: Audit the OpenTasks codebase against architecture rules defined in CLAUDE.md. Reports violations with file paths, line numbers, and fixes.
allowed-tools: Read, Grep, Glob, Agent
---

# Architecture Audit

Audit the codebase against **CLAUDE.md** rules. Report violations only — do not fix unless asked.

## Checks

Launch up to 3 Explore agents in parallel. Search the entire codebase — do not limit to known files.

### Agent 1: ViewModel & Domain Layer
- Per-screen ViewModels only — no cross-screen ViewModel usage
- ViewModels inject UseCases + Actions, never repositories
- Every read through a UseCase, every write through an Action
- DI wiring correct in `AppModule.kt`
- `Dispatchers.IO` for writes, `.flowOn(Dispatchers.Default)` for transforms
- `WhileSubscribed(5000)` on all UI state flows

### Agent 2: UI & Composables
- One composable = one UI component
- Screen/Content split — inner content extracted into a separate composable
- `@Preview` composables live in `androidMain`, not `commonMain`
- `collectAsState()` at lowest scope
- All text via `stringResource()`, all dp via `OpenTasksTheme.dimens` (except 0–2dp inline, previews)
- Screens pad for translucent overlay bars
- `LazyColumn` uses `key = { it.id }`
- No data transforms in composables — filtering/sorting/grouping in UseCases or ViewModels
- No duplicated composables — check `SharedComposables.kt`, `CalendarComposables.kt`, `CalendarTaskRows.kt`

### Agent 3: Data Layer, Sync & Date/Time
- **Strong skipping**: No `@Immutable` or `@Stable` annotations in codebase
- **Soft delete everywhere**: Repositories must use soft delete (`isDeleted = true, isSynced = false`), never `dao.delete()`. Exception: `ClearLocalDataAction` uses DAOs directly.
- **Default timestamps**: Repository `insert` methods must fill 0L `createdAt`/`updatedAt` with `localNow()` via `withDefaultTimestamps()`
- **DAO isDeleted filters**: All DAO queries returning user-visible data must filter `WHERE isDeleted = 0`
- No raw date literals (`86400000L`) — use DateTimeUtils or named constants
- Calendar views never filter out completed tasks
- No `!!` operator — use `?.let {}`, `?: return`, local `val`, or `?: default`
- **UTC↔local in repository only**:
  - Repositories have `withLocalTimestamps()` (reads) and `withUtcTimestamps()` (writes)
  - Actions use `localNow()`, not `utcNow()`
  - UI/ViewModels/UseCases never call `utcMillisToLocalMillis`, `localMillisToUtcMillis`, `computeDeadlineUtcMillis`, or `utcNow()`
  - UI date pickers use `computeLocalMillis()`
  - `ToggleTaskCompleteAction` uses `computeNextDeadlineLocal`
  - `ScheduleTaskRemindersAction` reads raw UTC via `getTaskByIdUtc()` — legitimately uses `utcNow()`
  - `RescheduleAllRemindersAction` uses `invokeWithUtcTask()` for `getTasksWithDeadlines()`
  - Exceptions: WidgetDataProvider and SyncService read DAOs directly. Import Actions convert external UTC to local at boundary.

## Report Format

```
**[RULE]** — file:line
Violation description.
Fix: what to change.
```

End with: total violations by category + compliant areas.
