# OpenTasks Architecture

Load this when working on architecture, source layout, dependency flow, or cross-layer behavior.

## Stack

- Kotlin Multiplatform Compose targeting Android, iOS, and JVM Desktop.
- Room database with `BundledSQLiteDriver` and KSP code generation.
- Koin DI with `sharedModule` in common code and per-platform `platformModule` database builders.
- AndroidX Navigation 3 for app navigation.
- `kotlinx-datetime` for date/time logic.

## Layering

- Data layer owns models, DAOs, repositories, database setup, sync adapters, and date/time boundary conversion.
- Domain layer owns read UseCases and write Actions.
- ViewModels are per screen and inject UseCases/Actions, never repositories.
- UI renders state and sends events. Filtering, sorting, grouping, and mapping belong in UseCases or ViewModels, not composables.
- Expensive screen projections and mode-specific UI state belong in the screen ViewModel. Keep transient view mode and selected-date state in memory unless there is an explicit persistence requirement.

## UseCases And Actions

- Reads use one UseCase class per read operation. UseCases return `Flow` via `operator fun invoke()`.
- Writes use one Action class per write operation. Actions expose `suspend operator fun invoke(...)`.
- Actions handle timestamps with `localNow()`; repositories convert local values to UTC for persistence.
- New features should create or reuse UseCases/Actions first, then wire them into the relevant ViewModel.

## Source Layout

```text
composeApp/src/commonMain/kotlin/.../
├── data/          # Models, DAOs, repositories, DB, sync
├── domain/        # UseCases and Actions
├── viewmodel/     # Per-screen ViewModels + AppViewModel
├── ui/            # Screens, theme, composables
└── di/            # Koin modules
```

Platform directories are `androidMain/`, `iosMain/`, and `jvmMain/`. Use `expect`/`actual` only when shared common code cannot reasonably handle the behavior.

## Android App Boundary

- `composeApp` is the KMP shared library. It owns common code, Android `actual` implementations, and Room/KSP generation; its Android library namespace is `com.udnahc.opentasks.shared`.
- `androidApp` is the Android application shell. It owns the application ID/version/build types, manifest, `OpenTasksApplication`, `MainActivity`, notification receivers, sync worker, widget components, and Android resources.
- Android component packages remain `com.udnahc.opentasks...` so the manifest names, Room database location, widget bindings, notifications, and WorkManager class names stay stable.
- Build the Android APK with `./gradlew :androidApp:assembleDebug`.

## Key Files

| Purpose | Path |
| --- | --- |
| App entry and navigation | `App.kt` |
| ViewModels | `viewmodel/` |
| UseCases and Actions | `domain/usecase/`, `domain/action/` |
| Database | `data/database/AppDatabase.kt` |
| DI modules | `di/AppModule.kt` |
| Date utilities | `data/extensions/DateTimeUtils.kt` |
| Models | `data/model/` |
| Version catalog | `gradle/libs.versions.toml` |

## PocketBase Sync

- PocketBase uses pre-created `users` accounts. Task UI is mounted only for `AccountSessionState.Authenticated` after a durable `CacheBinding` proves the single Room cache belongs to that endpoint, server instance, account, capability version, and epoch.
- All seven sync collections require an owner relation. PocketBase rules enforce owner-only access, and the structured gateway scopes every query/mutation and rejects raw cross-owner responses before DAO writes.
- `AccountMutationGate` is the process-wide boundary for user writes, sync mutation, account switch/logout, and one-cache replacement. Do not construct independent production gates.
- Switch/logout require an online source refresh, successful final sync, and zero unsynced rows. A durable transition marker makes crash recovery fail closed; task UI remains unmounted until the authoritative cache is activated and initially pulled.
- Authentication rejection requires same-account reauthentication. A connectivity-only refresh failure may enter offline mode only when an existing binding proves cache ownership.
- Account-bound delayed callbacks carry `accountId` and `boundaryEpoch`; receivers, workers, and widgets reject stale payloads before reading or mutating task data.
- Repositories soft-delete durable rows and trigger sync; `SyncService` and adapters use DAOs directly to avoid sync loops during pull.
- Collections sync in dependency order: categories, tags, tasks, attachments, task_tags, notes, countdowns.
- Each collection pulls before pushing and uses last-write-wins by local database `updatedAt` / server `localUpdatedAt`.
- Remote rows with newer timestamps overwrite local rows, including older unsynced local edits; unsynced local rows push only when newer. Equal timestamps succeed only when canonical payloads match.
- App deletes are server tombstones (`isDeleted = true`) retained indefinitely, not PocketBase hard deletes. Never-synced local tombstones without `pbId` may be hard-deleted locally.
- After a successful full fetch, synced active local rows missing from the server are marked unsynced so push recreates them.
- Task-tag assignments are synced as `task_tags` records with `localId = "$taskId:$tagId"` while keeping `(taskId, tagId)` as the local Room primary key.
- Clock skew between devices can make the wrong edit win because there is no conflict UI or history.

## Priority System

- `HIGH` = Urgent and Important, quadrant I, red.
- `MEDIUM` = Not Urgent and Important, quadrant II, amber.
- `LOW` = Urgent and Unimportant, quadrant III, blue.
- `NONE` = Not Urgent and Unimportant, quadrant IV, green.
