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

## Approved Boundary Exceptions

- Notification scheduling uses raw UTC through `getTaskByIdUtc()` and `getTasksWithDeadlines()`.
- `ScheduleTaskRemindersAction` may use UTC-specific reads.
- `ScheduleCountdownRemindersAction` may use UTC-specific reads and converts to local calendar recurrence before returning to the UTC scheduling boundary.
- `RescheduleAllRemindersAction` uses UTC task access for deadlines.
- `WidgetDataProvider` and `SyncService` may read DAOs directly.
- Import Actions convert external UTC inputs to local at the system boundary.

## Reminder Queue

- Reminder-domain text is supplied through `ReminderTextProvider`; production uses localized resources and tests use a resource-free provider.
- Android schedules one recurring occurrence at a time. The final alarm in an occurrence bundle chains the next task or countdown occurrence after validating that the record and occurrence are still active.
- iOS rebuilds a unified task-and-countdown queue capped at 60 pending requests. Queue selection reserves complete nearest-occurrence bundles per event before filling remaining capacity by trigger time, and request IDs include the occurrence timestamp.
- `RebuildReminderQueueAction` owns launch, resume, boot/background, post-sync, and iOS record-change queue rebuilding. Do not create a separate iOS per-feature queue.

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
- Remote rows overwrite local rows, including unsynced local edits, only when remote `localUpdatedAt` is newer.
- Unsynced local rows push when their timestamp is newer than or equal to the remote row.
- Normal synced deletes are pushed as durable PocketBase tombstones with `isDeleted = true`; do not hard-delete server rows for app deletes.
- Pull remote tombstones by upserting synced local tombstones.
- Hard-delete locally only for never-synced tombstones with no `pbId`.
- After a successful non-empty full fetch, physically missing server rows are treated as damage/manual deletion: synced active local rows absent from the remote `localId` set are marked unsynced for recreation.
- If a remote collection fetch returns zero rows while synced active local rows exist, treat sync as degraded and skip missing-row recovery; do not automatically recreate wiped collections during normal sync.
- Remote `task_tags` rows whose task or tag parent is missing are skipped and reported as degraded sync while valid links continue to merge.
- A collection push must be skipped when that collection's pull failed; dependent pushes must also be skipped when parent pulls fail.
- Push bookkeeping must mark rows synced only if `updatedAt` and `isDeleted` still match the pushed state.
- Task-tag assignments sync through `task_tags` with derived `localId = "$taskId:$tagId"` and local primary key `(taskId, tagId)`.
- `tasks.subtasks` is a synced JSON array string for editor subtask state. Each entry must contain exactly `id`, `text`, and `isChecked`; blank subtask rows are dropped before save. Existing task content is not automatically migrated into this field.
- Device clock skew is a known limitation: a bad device clock can incorrectly win last-write-wins conflicts.
