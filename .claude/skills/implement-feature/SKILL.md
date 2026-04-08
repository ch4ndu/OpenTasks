---
name: implement-feature
description: Use when implementing a new feature, screen, or significant change in the OpenTasks KMP app. Guides through proper architecture patterns, platform considerations, and verification.
argument-hint: [feature description]
allowed-tools: Read, Write, Edit, Grep, Glob, Bash, Agent
---

# Implement Feature

Refer to **CLAUDE.md** for conventions. This is the implementation checklist.

## Before Writing Code

1. Read CLAUDE.md
2. Explore existing code — reuse UseCases, Actions, composables, utilities
3. Identify scope: commonMain only or platform-specific?
4. For non-trivial tasks: launch Architect + Critic Plan agents in parallel

## Implementation Checklist

### Data Layer (new entity or schema change)
- [ ] Entity in `data/model/`
- [ ] DAO in `data/dao/` — `Flow<>` reads, `suspend` writes. User-visible queries filter `WHERE isDeleted = 0`
- [ ] Repository interface + impl in `data/repository/`
  - `withLocalTimestamps()` on all reads (UTC→local)
  - `withUtcTimestamps()` on all writes (local→UTC)
  - `withDefaultTimestamps()` on insert — fill 0L timestamps with `localNow()`
  - Soft delete on `delete()` — `dao.update(entity.copy(isDeleted = true, isSynced = false))`, never `dao.delete()`
- [ ] Sync adapter + record in `data/sync/` if entity syncs to PocketBase
- [ ] Update `AppDatabase.kt`: entities, DAO accessor, bump version
- [ ] Register repository (and sync adapter if applicable) in `di/AppModule.kt`

### Domain Layer
- [ ] UseCase per read in `domain/usecase/{entity}/`
- [ ] Action per write in `domain/action/{entity}/`
- [ ] Derived/filtered flows in UseCases, not ViewModels
- [ ] Register in `di/AppModule.kt` — `single {}` or `factory {}` for runtime params

### ViewModel
- [ ] New screen → new ViewModel in `viewmodel/`
- [ ] Inject UseCases + Actions, never repositories
- [ ] Shared sheet ops (create/edit task or note) → `AppViewModel`
- [ ] Register in `di/AppModule.kt` via `viewModel { ... }`

### UI Layer
- [ ] Screen composable in `ui/screens/`
- [ ] ViewModel via `koinViewModel()` at call site in `App.kt`
- [ ] Wire in `App.kt` if new tab or destination

### Platform
- [ ] Prefer commonMain — `expect`/`actual` only when necessary

## Post-Code Audit

### Correctness
- [ ] No `!!` — use `?.let {}`, `?: return`, local `val`, or `?: default`
- [ ] Completed tasks never filtered out in calendar views
- [ ] All dp via `OpenTasksTheme.dimens`, all strings via `stringResource()`
- [ ] Bottom/top padding accounts for translucent overlay bars

### Date/Time
- [ ] All UTC↔local conversions in repositories only
- [ ] Actions use `localNow()`, not `utcNow()`
- [ ] UI date pickers use `computeLocalMillis()`
- [ ] No `utcMillisToLocalMillis`/`localMillisToUtcMillis`/`computeDeadlineUtcMillis` outside data layer
- [ ] Notification scheduling reads raw UTC via `getTaskByIdUtc()` or `getTasksWithDeadlines()`

### Reuse
- [ ] No duplicated composables — check `SharedComposables.kt`, `CalendarComposables.kt`, `CalendarTaskRows.kt`

### Recomposition
- [ ] Strong skipping enabled — no `@Immutable` or `@Stable` annotations
- [ ] No data transforms in composables
- [ ] `collectAsState()` at lowest scope
- [ ] `LaunchedEffect` keys are narrow
- [ ] Magic numbers extracted to `const val`

### Previews
- [ ] `@Preview` composables go in `androidMain`, not `commonMain`
- [ ] Previews must not call `kotlinx-datetime` — use `PreviewSampleData`

## Build Verification

1. `./gradlew :composeApp:compileKotlinJvm`
2. Grep for: `!!`, inline `86400000`, hardcoded strings, `.filter { !it.isCompleted }` in calendar

## Feature Request

$ARGUMENTS
