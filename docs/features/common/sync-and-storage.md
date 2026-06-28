# Sync and Storage

## Overview

OpenTasks is local-first. Room is the primary persistence layer, and PocketBase sync is optional for backup and multi-device use.

The app is designed for trusted app instances against a self-hosted PocketBase server with public collection rules. It does not currently implement user accounts or per-user authorization.

## Local Storage

The shared database is `AppDatabase`, backed by Room and `BundledSQLiteDriver`. Entities include tasks, categories, notes, tags, task-tag assignments, app settings, countdowns, and attachments.

Repositories wrap DAOs for normal app reads and writes. Repositories convert between local app timestamps and UTC database timestamps. DAOs are used directly only at explicit boundary layers such as sync and Android widgets.

Schema changes require explicit Room migrations. The current attachment work adds database version 11 with the local `attachments` table.

## Soft Deletes

User deletes are soft deletes for synced durable entities. Rows are marked `isDeleted = true` and `isSynced = false` so the delete can propagate to PocketBase as a tombstone.

Never-synced local tombstones without a PocketBase id may be hard-deleted locally. Normal synced deletes are not hard-deleted from the server by app behavior.

## PocketBase Sync

Sync is collection-based and runs through `SyncService`. Each adapter pulls remote records before pushing local unsynced rows. The current dependency order is:

1. categories
2. tags
3. tasks
4. attachments
5. task_tags
6. notes
7. countdowns

Conflict resolution is last-write-wins using app-managed `updatedAt` values stored remotely as `localUpdatedAt`. Device clock skew can make the wrong edit win because there is no conflict UI.

If a collection pull fails, that collection push is skipped. Dependent pushes are also skipped when a parent pull fails. For example, attachments depend on tasks, and task-tag assignments depend on tasks and tags.

After a successful non-empty full fetch, a synced active local row missing from the remote collection is treated as server damage/manual deletion and marked unsynced so the next push can recreate it. If the server returns zero records while synced active local rows exist, sync treats the collection as degraded and skips missing-row recovery.

## Connection Verification

When a user saves a PocketBase URL, the app verifies the server before persisting the setting. Verification performs a health check and asks each sync adapter to verify its collection. The app then runs an initial sync using the verified client. Only after those steps pass does it save the URL and configure the active sync client.

## Attachment Files

Attachment metadata syncs as a PocketBase record. Attachment binary content syncs through a PocketBase file field. Local optimized files and thumbnails are stored outside Room and referenced by path.

See [Attachments](attachments.md) for image policy, sync states, and file-specific behavior.

## Clear Local Data

Settings exposes a local reset action. `ClearLocalDataAction` clears local repositories/settings and attachment file storage so the app can return to a clean local state. PocketBase server data is not deleted by clearing local data.

## Related Docs

- [Attachments](attachments.md)
- [Import and Export](import-export.md)
- [Settings](../settings.md)
