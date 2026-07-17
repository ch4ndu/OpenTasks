# Data, Sync, And Date/Time Rules

Load this for Room, DAOs, repositories, migrations, sync, import/export, reminders, or timestamp work.

## Database

- Room uses `BundledSQLiteDriver` and KSP code generation.
- App database version changes require explicit migrations. Do not use destructive fallback.
- DAOs expose `Flow` for reads and `suspend` functions for writes.
- User-visible DAO queries filter `WHERE isDeleted = 0`.

## Repository Rules

- DAOs are wrapped by repository interfaces and implementations.
- Repositories are the only normal layer that converts between UTC and local timestamps.
- Reads convert UTC to local with `withLocalTimestamps()`.
- Writes convert local to UTC with `withUtcTimestamps()`.
- Inserts fill zero `createdAt` and `updatedAt` values with `localNow()`.
- Deletes are soft deletes: update the entity with `isDeleted = true` and `isSynced = false`.
- A retained soft-delete path must stamp `updatedAt` with `localNow()` before storage conversion. Remove repository delete methods with no callers rather than retaining stale write paths.
- Read `Flow`s apply `distinctUntilChanged()` after timestamp conversion so Room re-emissions with identical content (for example sync-only `isSynced`/`updatedAt` churn) do not propagate to ViewModels or trigger recomposition.

## Date/Time

- Database stores UTC epoch millis.
- UI, ViewModels, Actions, and UseCases should work with local millis unless explicitly at a system boundary.
- Actions use `localNow()` for timestamps.
- UI date pickers use `computeLocalMillis()`.
- Use `kotlinx-datetime` and `data/extensions/DateTimeUtils.kt` for date math.
- Do not use raw day millis literals such as `86400000L`; use utilities or named constants.
- Date-relative ViewModel projections consume the shared `LocalDaySignal`; it checks for local-day rollover while collected and is refreshed on app resume.
- Countdown recurrence is derived from the immutable stored target. Countdown mode projects the next occurrence, count-up mode projects the latest reached occurrence, and calendar projection emits one effective occurrence.
- One-shot active task queries filter tombstones; widget task-list reads additionally exclude DONE, while calendar/week range reads intentionally include DONE.

## Approved Boundary Exceptions

- Notification scheduling uses raw UTC through `getTaskByIdUtc()` and `getTasksWithDeadlines()`.
- `ScheduleTaskRemindersAction` may use UTC-specific reads.
- `ScheduleCountdownRemindersAction` may use UTC-specific reads and converts to local calendar recurrence before returning to the UTC scheduling boundary.
- Countdown reminder reconciliation uses the explicitly tombstone-inclusive `getAllCountdownsForReminderReconciliationUtc()` query so removed occurrences can be cancelled.
- `RescheduleAllRemindersAction` uses UTC task access for deadlines.
- `WidgetDataProvider` and `SyncService` may read DAOs directly.
- Import Actions convert external UTC inputs to local at the system boundary.

## Reminder Queue

- Reminder-domain text is supplied through `ReminderTextProvider`; production uses localized resources and tests use a resource-free provider.
- Android schedules one recurring occurrence at a time. The final alarm in an occurrence bundle chains the next task or countdown occurrence after validating that the record and occurrence are still active.
- iOS rebuilds a unified task-and-countdown queue capped at 60 pending requests. Queue selection reserves complete nearest-occurrence bundles per event before filling remaining capacity by trigger time, and request IDs include the occurrence timestamp.
- `RebuildReminderQueueAction` owns launch, resume, boot/background, post-sync, and iOS record-change queue rebuilding. Do not create a separate iOS per-feature queue.
- iOS background work reports completion only after sync and every notification-queue add callback completes. Expiration cancels the Kotlin job; completion arbitration must call `setTaskCompleted` exactly once.
- Every reminder identity is `eventId + occurrence UTC millis + reminder kind + pre-filter ordinal`. Build the ordinal with `mapIndexed` before removing past/invalid triggers, and carry that same semantic key through queue selection, platform scheduling, delivery, actions, and cancellation.
- Android maps semantic keys to persisted positive request IDs in its SharedPreferences-backed reminder store. The store indexes event-to-key and tracks pending/displayed lifecycle; event cancellation must use that index rather than scanning fixed integer slots. Fixed-slot cleanup is permitted only by the versioned one-time legacy migration.
- Android alarm, tap, and action PendingIntents use the allocated ID plus a unique data URI containing the semantic key and role. Receivers must remove stale, inactive, denied, and action-consumed keys through the scheduler/store APIs.
- Notification payload times are an approved UTC boundary: scheduler and platform receivers retain occurrence/trigger instants in UTC, then the task notification completion path converts the occurrence to local only when passing it into the persisted-truth task-write boundary. iOS uses an absolute time-interval trigger derived from the UTC instant so a later timezone change does not shift a pending delivery.

## Sync

- Repositories trigger sync automatically on inserts, updates, and deletes.
- Clear Local Data must enter the exclusive sync-reset boundary before cleanup: reject new sync requests, cancel pending debounce work, disconnect PocketBase, wait for any active pass, and keep the provider disconnected if cleanup fails.
- Clear Local Data deletes every Room entity in one writer transaction, recreates Inbox in that transaction, and clears attachment files before leaving the exclusive reset boundary.
- ViewModels and ordinary Actions should not call `TriggerSyncAction`.
- Approved direct sync triggers are Settings “Sync Now”, app resume sync, widget refresh callbacks, and `AppViewModel.triggerSync()` for pull-to-refresh.
- `SyncService` uses DAOs directly to avoid sync loops during pull.
- Sync runs collection-by-collection in dependency order: categories, tags, tasks, attachments, task_tags, notes, countdowns.
- Each collection pulls before pushing.
- Conflict resolution uses last-write-wins by app-managed UTC `updatedAt` / PocketBase `localUpdatedAt`.
- Pull merges reread and compare inside one Room writer transaction. A collection snapshot may identify missing remote IDs only; it must never authorize an overwrite.
- PocketBase migration 010 guards every sync collection update with `localUpdatedAt >` the stored timestamp. Equal timestamps are accepted locally only when canonical payloads match; divergent equal-timestamp writes fail closed. Sync-critical mutations use the structured record gateway; do not infer HTTP status from exception text.
- Candidate PocketBase URLs are classified through detached health/capability and complete paginated seven-collection inventory reads before any provider swap, `syncAll`, local reset, or remote mutation. Persist the canonical URL, server identity, and sync mode in one writer transaction. A populated replacement may only be adopted by fresh local storage; a nonempty local store moving to an empty replacement remains in `EMPTY_SERVER_SEED_PENDING` until the dedicated seed/resume path validates every row and tombstone.
- Pending empty-server migration is executed by `ServerSeedExecutor` through dependency-ordered guarded adapter writes. It revalidates the committed identity and complete inventory before resume, retains the marker after every failure, and clears it only when a final inventory exactly matches all local active rows and tombstones (with blank files for attachment tombstones). Normal sync must not bypass this executor.
- Attachment downloads write bytes outside Room, then conditionally install the paths after a writer-boundary reread. A newer local edit or tombstone keeps its metadata and deletes the losing downloaded artifact; equal timestamps only reinstall a retryable download failure.
- A new attachment tombstone creates JSON metadata without `file` or `file-`. An existing remote attachment is cleared only with the exact `file-` filename, and local remote-file/error cleanup occurs only after a blank-file response and an unchanged local tombstone check.
- `SyncService` serializes every requested pass: callers wait for their own pass. `TriggerSyncAction` protects debounce replacement with a mutex and `syncNow` cancels pending debounce work before awaiting the service.
- Remote rows overwrite local rows, including unsynced local edits, only when remote `localUpdatedAt` is newer.
- Unsynced local rows update an existing remote row only when their timestamp is strictly newer. At an equal timestamp, mark synced only when the canonical payload matches; divergent payloads fail closed.
- Normal synced deletes are pushed as durable PocketBase tombstones with `isDeleted = true`; do not hard-delete server rows for app deletes.
- Task deletion is a single Room writer-boundary graph mutation: tombstone active task-tag links and task attachments before tombstoning the task, then perform best-effort cleanup of never-uploaded file bytes only after commit. A failed database mutation leaves the complete graph and files unchanged; a failed file cleanup leaves the durable tombstones recoverable.
- Pull remote tombstones by upserting synced local tombstones.
- An active remote attachment with a blank file is degraded sync, not a tombstone; retain local files. Delete attachment files only after a winning remote tombstone is persisted.
- Attachment hard delete is only valid for rows without a PocketBase identity.
- Hard-delete locally only for never-synced tombstones with no `pbId`. Before deleting a tag tombstone, retain it when any related `task_tags` row has a remote identity, so the foreign-key cascade cannot discard that durable relation.
- After a successful non-empty full fetch, physically missing server rows are treated as damage/manual deletion: synced active local rows absent from the remote `localId` set are marked unsynced for recreation.
- If a remote collection fetch returns fewer than ten percent of synced active local rows (including zero), treat sync as degraded and skip missing-row recovery; fail that collection's pull so neither it nor dependent collections push during the pass.
- Remote active `task_tags` rows whose task or tag parent is missing are skipped and reported as degraded sync; remote tombstones with a missing parent are safely skipped so valid links can continue to merge and push.
- A collection push must be skipped when that collection's pull failed; dependent pushes must also be skipped when parent pulls fail.
- Push bookkeeping must mark rows synced only if `updatedAt` and `isDeleted` still match the pushed state.
- Task-tag assignments sync through `task_tags` with derived `localId = "$taskId:$tagId"` and local primary key `(taskId, tagId)`.
- Task-tag restore and tombstone writes preserve `createdAt` inside their DAO transaction; a repository-side read followed by upsert is not safe.
- `tasks.subtasks` is a synced JSON array string for editor subtask state. Each entry must contain exactly `id`, `text`, and `isChecked`; blank subtask rows are dropped before save. Existing task content is not automatically migrated into this field.
- Device clock skew is a known limitation: a bad device clock can incorrectly win last-write-wins conflicts.
