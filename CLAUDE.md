# OpenTasks

Kotlin Multiplatform Compose app for task management with Eisenhower Matrix prioritization.

## Build & Verify

- Quick build check: `./gradlew :composeApp:compileKotlinJvm :composeApp:compileKotlinIosSimulatorArm64`
- Android: Build/run via Android Studio or `./gradlew :composeApp:assembleDebug`
- iOS: Open `iosApp/iosApp.xcodeproj` in Xcode, build the `iosApp` scheme
- Desktop: `./gradlew :composeApp:run`

## Architecture

- **KMP Compose Multiplatform** targeting Android, iOS (arm64 + simulator), JVM Desktop
- **Room** database with `BundledSQLiteDriver`, KSP code generation. Version 3, entities: `Task`, `Category`, `Note`, `Tag`, `TaskTag`. Explicit migrations required (no destructive fallback).
- **Koin** DI: `sharedModule` (common) + `platformModule` (per-platform DB builder)
- **Repository pattern**: DAOs wrapped by repository interfaces (`data/repository/`)
- **UseCase/Action pattern**: Reads → UseCase classes (`domain/usecase/`), writes → Action classes (`domain/action/`). UseCases expose `operator fun invoke()` returning `Flow`. Actions expose `suspend operator fun invoke(...)` and handle timestamps.
- **Per-screen ViewModels** injected with UseCases/Actions (never repositories directly). `AppViewModel` for shared sheet operations. Screen VMs: `MatrixViewModel`, `TaskListViewModel`, `CalendarViewModel`, `NoteViewModel`.
- Platform-specific code uses `expect`/`actual`. Minimize platform code — prefer commonMain.

## Source Layout

```
composeApp/src/commonMain/kotlin/.../
├── data/          # Models, DAOs, repositories, DB
├── domain/        # UseCases (reads) and Actions (writes)
│   ├── usecase/   # task/, tasklist/, note/
│   └── action/    # task/, tasklist/, note/
├── viewmodel/     # Per-screen ViewModels + AppViewModel
├── ui/            # Screens, theme, composables
└── di/            # Koin modules
```

Platform dirs: `androidMain/`, `iosMain/`, `jvmMain/` — DB builders, BackHandler, entry points.

## Key Conventions

### Nullability
- **Never use `!!`** (non-null assertion operator). Use safe alternatives: `?.let { }`, `?: return`, `val x = nullable ?: default`, or local `val` for smart casts.

### Date/Time
- Database stores **UTC epoch millis** (`Long`)
- **All UTC↔local conversions happen in the repository layer only** — never in UI, ViewModels, Actions, or UseCases
- Repository converts UTC → "local millis" on read (`withLocalTimestamps()`)
- Repository converts "local millis" → UTC on write (`withUtcTimestamps()`)
- Actions use `localNow()` for timestamps (not `utcNow()`) — the repository handles the local→UTC conversion
- UI date pickers use `computeLocalMillis()` to produce local millis from date/time components
- Use `kotlinx-datetime` and `data/extensions/DateTimeUtils.kt` for all date math
- Notification scheduling uses raw UTC via `getTaskByIdUtc()` / `getTasksWithDeadlines()` — the only code that legitimately uses `utcNow()`
- Exceptions: `WidgetDataProvider` and `SyncService` read DAOs directly (boundary conversion OK). Import Actions convert external UTC inputs to local at the system boundary via `utcToLocal()`.

### UI
- Material3 with custom theme (`ui/theme/`)
- Bottom NavigationBar and top app bars **overlay** content (translucent, 0.8 alpha) — screens must pad accordingly
- All dp values via `OpenTasksTheme.dimens` (exception: 0–2dp inline spacing, preview containers)
- All strings via `stringResource()`, icons from `composeResources/drawable/`

### Composable Architecture
- **Single responsibility**: One composable = one UI component
- **Screen/Content split**: Screens and bottom sheets extract inner content into a separate composable that receives state and renders
- **Previews in androidMain**: All `@Preview` composables live in `androidMain` (not `commonMain`) due to tooling constraints
- **Deferred reads**: Pass `StateFlow` to children, collect at lowest possible scope
- **Reusable composables**: Before creating a new composable, check `ui/screens/SharedComposables.kt` and `ui/screens/calendar/CalendarComposables.kt` + `CalendarTaskRows.kt` for existing shared composables. If a similar pattern exists in 2+ screens, extract it into a shared file rather than duplicating.

### Domain Layer
- **UseCases**: One class per read. Constructor-injected with repository. Derived/filtered flows (groupBy, filter, combine) belong here.
- **Actions**: One class per write. Constructor-injected with repository. Uses `localNow()` for timestamps — repositories handle the local→UTC conversion. Exception: `ClearLocalDataAction` uses DAOs directly to avoid triggering sync on bulk delete.
- New features: create UseCases/Actions first, then wire into a screen-specific ViewModel.

### Sync
- **Repositories auto-sync**: `TaskRepositoryImpl`, `NoteRepositoryImpl`, `CategoryRepositoryImpl`, `CountdownRepositoryImpl`, and `TagRepositoryImpl` call `TriggerSyncAction` on every `insert`/`update`/`delete`. Do **not** call `triggerSyncAction()` from ViewModels or Actions — the repository handles it.
- **Soft delete everywhere**: All repositories use soft delete (`isDeleted = true, isSynced = false`) — never hard delete via `dao.delete()`. This ensures deletions propagate to PocketBase on sync. `BaseSyncAdapter.pushAll()` hard-deletes locally after the server-side delete succeeds.
- **Default timestamps**: Repository `insert` methods fill in `createdAt`/`updatedAt` with `localNow()` if they are 0L, ensuring new entities always have valid timestamps for sync conflict resolution.
- **Conflict resolution**: Server wins when remote `updatedAt` > local `updatedAt` during pull.
- `SyncService` uses DAOs directly (not repositories) to avoid infinite sync loops during pull.
- **Authorized direct callers of `TriggerSyncAction`**: (1) `SettingsViewModel` "Sync Now" button, (2) `App.kt` `LifecycleResumeEffect` for on-resume sync, (3) Widget refresh callbacks (`TaskRefreshCallback`, `CalendarRefreshCallback`, `WeekRefreshCallback`).

### Performance
- **Strong skipping enabled** — do not add `@Immutable` or `@Stable` annotations
- No data transformation in composables — filtering, sorting, mapping, grouping all belong in UseCases or ViewModels
- `Dispatchers.IO` for DB writes, `.flowOn(Dispatchers.Default)` for flow transforms
- `LazyColumn` with `key = { it.id }` for scrollable lists

### Priority System (Eisenhower Matrix)
- `HIGH` = Urgent & Important (I, red) | `MEDIUM` = Not Urgent & Important (II, amber)
- `LOW` = Urgent & Unimportant (III, blue) | `NONE` = Not Urgent & Unimportant (IV, green)

### Glance Widgets (Android)
- Widget code in `androidMain/.../widget/`
- **Colors MUST use resource IDs**: `ColorProvider(R.color.xxx)`, NOT `ColorProvider(0xFFxxxxxx.toInt())`. Raw color ints cause silent widget crashes.
- Define widget colors in `androidMain/res/values/colors.xml`
- Use `actionStartActivity(Intent)` with `ComponentName` for click actions (not `LocalContext.current`)
- **Data fetching in `provideContent`**: Use `produceState` keyed on Glance state, NOT closures from `provideGlance`. `provideGlance` runs once per session; `update()` only recomposes `provideContent`.
- Use `updateAppWidgetState` to bump a refresh trigger + `update()` to trigger fresh data fetch via state change.
- Use `TaskWidget.refreshWidget()` / `refreshAllWidgets()` — never call `instance.update()` directly.

## Key Files

| Purpose | Path |
|---------|------|
| App entry / navigation | `App.kt` |
| ViewModels | `viewmodel/{App,Matrix,TaskList,Calendar,Note}ViewModel.kt` |
| UseCases / Actions | `domain/{usecase,action}/{task,tasklist,note}/` |
| Database | `data/database/AppDatabase.kt` |
| DI modules | `di/AppModule.kt` |
| Date utilities | `data/extensions/DateTimeUtils.kt` |
| Models | `data/model/{Task,TaskList,Note}.kt` |
| Version catalog | `gradle/libs.versions.toml` |
