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

## Date/Time

- Database stores UTC epoch millis.
- UI, ViewModels, Actions, and UseCases should work with local millis unless explicitly at a system boundary.
- Actions use `localNow()` for timestamps.
- UI date pickers use `computeLocalMillis()`.
- Use `kotlinx-datetime` and `data/extensions/DateTimeUtils.kt` for date math.
- Do not use raw day millis literals such as `86400000L`; use utilities or named constants.

## Approved Boundary Exceptions

- Notification scheduling uses raw UTC through `getTaskByIdUtc()` and `getTasksWithDeadlines()`.
- `ScheduleTaskRemindersAction` may use UTC-specific reads.
- `RescheduleAllRemindersAction` uses UTC task access for deadlines.
- `WidgetDataProvider` and `SyncService` may read DAOs directly.
- Import Actions convert external UTC inputs to local at the system boundary.

## Sync

- Repositories trigger sync automatically on inserts, updates, and deletes.
- ViewModels and ordinary Actions should not call `TriggerSyncAction`.
- Approved direct sync triggers are Settings “Sync Now”, app resume sync, widget refresh callbacks, and `AppViewModel.triggerSync()` for pull-to-refresh.
- `SyncService` uses DAOs directly to avoid sync loops during pull.
- Server wins when remote `updatedAt` is newer than local `updatedAt`.
- `BaseSyncAdapter.pushAll()` may hard-delete locally after the server-side delete succeeds.
