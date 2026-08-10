# Sync and Storage

## Overview

OpenTasks is local-first. Room is the primary persistence layer, and PocketBase provides authenticated identity plus multi-device synchronization.

The first multi-user release supports two pre-created PocketBase accounts. Every synchronized record has a required owner; server rules and the owner-scoped client gateway prevent one account from reading or writing the other account's records.

## Local Storage

The shared database is `AppDatabase`, backed by Room and `BundledSQLiteDriver`. Entities include tasks, categories, notes, tags, task-tag assignments, app settings, countdowns, and attachments.

Repositories wrap DAOs for normal app reads and writes. Repositories convert between local app timestamps and UTC database timestamps. DAOs are used directly only at explicit boundary layers such as sync and Android widgets.

Schema changes require explicit Room migrations. The current Room schema is version 12. Account ownership does not add an account column to every local entity; one durable `CacheBinding` authorizes the installation's single active cache.

## Soft Deletes

User deletes are soft deletes for synced durable entities. Rows are marked `isDeleted = true` and `isSynced = false` so the delete can propagate to PocketBase as a tombstone.

Never-synced local tombstones without a PocketBase id may be hard-deleted locally. Normal synced deletes are not hard-deleted from the server by app behavior.

## PocketBase Sync

Sync is collection-based and runs through `SyncService` only with an authenticated provider binding. Each adapter pulls owner-scoped remote records before pushing local unsynced rows. The current dependency order is:

1. categories
2. tags
3. tasks
4. attachments
5. task_tags
6. notes
7. countdowns

Conflict resolution is last-write-wins using app-managed `updatedAt` values stored remotely as `localUpdatedAt`. Equal timestamps are accepted only when canonical payloads match. Device clock skew can make the wrong edit win because there is no conflict UI.

If a collection pull fails, that collection push is skipped. Dependent pushes are also skipped when a parent pull fails. For example, attachments depend on tasks, and task-tag assignments depend on tasks and tags.

After a successful non-empty full fetch, a synced active local row missing from the remote collection is treated as server damage/manual deletion and marked unsynced so the next push can recreate it. If the server returns zero records while synced active local rows exist, sync treats the collection as degraded and skips missing-row recovery.

## Account and Cache Boundary

Signed-out login authenticates a detached client and validates capability plus complete owner-scoped inventory before activation. Task navigation is created only for an authenticated session whose endpoint, server, account, capability, and epoch match the durable cache binding. A connectivity-only refresh failure may use that proven cache offline; authentication or binding failure keeps task UI hidden.

Account switch and logout run under the process-wide mutation gate. They refresh and fully synchronize the source online, require zero unsynced rows, invalidate account-bound platform work, and then replace the one local cache. A durable transition marker determines crash recovery before versus after the cache transaction.

## Attachment Files

Attachment metadata syncs as an owner-scoped PocketBase record. Attachment binary content uses a protected PocketBase file field and short-lived file tokens. Local optimized files and thumbnails are stored outside Room and referenced by path.

See [Attachments](attachments.md) for image policy, sync states, and file-specific behavior.

## Platform Credential Storage

Android stores tokens behind an Android Keystore AES/GCM key, iOS uses a device-only Keychain item, and macOS desktop uses the login keychain. Windows/Linux use an owner-only application file when supported and display a weaker-storage warning. Passwords are never persisted.

## Related Docs

- [Attachments](attachments.md)
- [Import and Export](import-export.md)
- [Settings](../settings.md)
