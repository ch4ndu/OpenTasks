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
- Expensive screen projections and mode-specific UI state belong in the screen ViewModel. Keep transient view mode and selected-date state in memory unless there is an explicit persistence requirement. Calendar row/day projections are immutable, equality-reused values built on `Dispatchers.Default`; measured-height prefix selection is the only layout-boundary exception.

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

- A durable `CacheBinding` authorizes the single Room cache in either `LOCAL_ONLY` or `POCKETBASE` mode. Task UI is mounted for `AccountSessionState.LocalOnly` or `AccountSessionState.Authenticated`; restoring, transitions, and invalid mixed token/binding states keep it unmounted.
- Local-only mode uses a reserved local owner plus boundary epoch and must not initialize PocketBase. PocketBase mode uses pre-created `users` accounts and proves endpoint, server instance, account, capability version, and epoch before activation.
- All seven sync collections require an owner relation. PocketBase rules enforce owner-only access, and the structured gateway scopes every query/mutation and rejects raw cross-owner responses before DAO writes.
- `AccountMutationGate` is the process-wide boundary for user writes, active-cache callbacks, sync mutation, local clear, account switch/logout, and one-cache replacement. Do not construct independent production gates.
- Local foreground/background work uses active-cache boundaries. Provider activation, normal sync, manual/pull refresh, account switching, logout, and reauthentication remain PocketBase-only.
- Switch/logout require an online source refresh, successful final sync, and zero unsynced rows. A durable transition marker makes crash recovery fail closed; task UI remains unmounted until the authoritative cache is activated and initially pulled.
- Local clear persists `LOCAL_CLEAR/PRE_RESET` before resetting Room and `FILES_PENDING` before attachment cleanup; recovery resumes the indicated phase and converges to signed out.
- Local-to-PocketBase connect is an explicitly confirmed authoritative replacement. Preflight is detached and count-only; confirmation revalidates opaque complete local/owner inventory fingerprints under the mutation gate, persists the transition before remote mutation, deletes only destination-owner rows, resets all local sync metadata without deleting content/files, exact-seeds, and activates only after final inventory equality. Any concurrent destination change retries through full delete/reset/reseed.
- Authentication rejection requires same-account reauthentication. A connectivity-only refresh failure may enter offline mode only when an existing binding proves cache ownership.
- Every detached or replaced PocketBase client has one owner and one idempotent close/unregister path. Candidate clients are completely configured before publication; failed setup leaves the previous active client usable.
- `AccountClientSession` owns temporary authenticator HTTP clients. Every authenticate, refresh, capability, inventory, and validation session closes on success, ordinary failure, and cancellation.
- Account-bound delayed callbacks carry `accountId` and `boundaryEpoch`; receivers, workers, and widgets reject stale payloads before reading or mutating task data.
- Repositories soft-delete durable rows and trigger sync; `SyncService` and adapters use DAOs directly to avoid sync loops during pull.
- Collections sync in dependency order: categories, tags, tasks, attachments, task_tags, notes, countdowns.
- Each collection pulls before pushing and uses last-write-wins by local database `updatedAt` / server `localUpdatedAt`.
- Remote rows with newer timestamps overwrite local rows, including older unsynced local edits; unsynced local rows push only when newer. Equal timestamps succeed only when canonical payloads match.
- App deletes are server tombstones (`isDeleted = true`) retained indefinitely, not PocketBase hard deletes. Never-synced local tombstones without `pbId` may be hard-deleted locally.
- Owner hard deletion is permitted only inside a confirmed local-authoritative replacement against migration 012 capability `authoritativeReplaceVersion = 1`; it must never replace normal tombstone behavior.
- After a successful full fetch, synced active local rows missing from the server are marked unsynced so push recreates them.
- Task-tag assignments are synced as `task_tags` records with `localId = "$taskId:$tagId"` while keeping `(taskId, tagId)` as the local Room primary key.
- Clock skew between devices can make the wrong edit win because there is no conflict UI or history.

## External boundaries and cleanup

- External file/share data is bounded by byte count and decoded with strict UTF-8 before parsing or navigation. Rejections are typed and localized; raw platform exception text and user-authored content do not enter diagnostics.
- Native share handoffs carry user content through a bounded, single-use platform container. The iOS extension publishes a 24-hour pending envelope before reporting success and does not depend on opening the containing app. Launch, foreground activation, and exact-shape nonce URLs only signal one serialized scanner. It may claim while the app is active and the active account UI is mounted and idle, after reserving a ticket for that readiness generation and owner/epoch; backgrounding or another readiness change invalidates publication from that ticket. An established review lease survives ordinary background/resume. The shared consumer atomically acquires one review lease while removing the queue head and holds it through task-editor exit or the terminal confirmed ICS-import path.
- Every temporary or replaced native resource has one owner and one idempotent cleanup path. This includes PocketBase clients, `AccountClientSession` HTTP engines, attachment files, and JVM Calendar child processes. Attachment file ownership is durably leased in Room before the first write; exact row rereads transfer a referenced lease, while deletion must prove absence before releasing an unreferenced lease.
- Cancellation remains cancellation across file import, export handoff, attachment work, sync, Settings, and authenticator sessions. Do not convert `CancellationException` into ordinary failure state or continue destructive work after cancellation.
- JVM Calendar commands use a timeout- and 16 MiB-output-bounded process runner that drains merged output without deadlock and destroys/forcibly destroys children on timeout, cancellation, or overflow.

## Quick Add

- Quick Add parsing is a pure common UseCase. The entry-scoped ViewModel owns the stable reference time, recognized/dismissed tokens, validation, save state, and captured active-cache boundary; composables never parse input.
- The parser is offline, deterministic, English-only, suffix/token-bounded, and limited to the grammar documented in `docs/features/tasks.md`. Do not add fuzzy matching, AI/network parsing, categories, tags, priorities, reminders, or arbitrary recurrence intervals without a new approved contract.
- Quick Add and the full task editor receive the same opening-surface category, priority, and optional Calendar civil date. Explicit parsed date/time/recurrence overrides only its corresponding inferred field. Persistence reuses `AddTaskAction`.

## Priority System

- `HIGH` = Urgent and Important, quadrant I, red.
- `MEDIUM` = Not Urgent and Important, quadrant II, amber.
- `LOW` = Urgent and Unimportant, quadrant III, blue.
- `NONE` = Not Urgent and Unimportant, quadrant IV, green.
