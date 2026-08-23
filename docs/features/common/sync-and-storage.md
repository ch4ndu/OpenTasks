# Sync and Storage

## Overview

OpenTasks is local-first. Room is the primary persistence layer. PocketBase is optional: Local only mode provides the complete local feature set without creating a PocketBase client, while PocketBase mode adds authenticated identity and multi-device synchronization.

The first multi-user release supports two pre-created PocketBase accounts. Every synchronized record has a required owner; server rules and the owner-scoped client gateway prevent one account from reading or writing the other account's records.

## Local Storage

The shared database is `AppDatabase`, backed by Room and `BundledSQLiteDriver`. Entities include tasks, categories, notes, tags, task-tag assignments, app settings, countdowns, and attachments.

Repositories wrap DAOs for normal app reads and writes. Repositories convert between local app timestamps and UTC database timestamps. DAOs are used directly only at explicit boundary layers such as sync and Android widgets.

Schema changes require explicit Room migrations. The current Room schema is version 13. Account ownership does not add an account column to every local entity; one durable `CacheBinding` authorizes the installation's single active cache. Its mode is either `LOCAL_ONLY`, using the reserved local owner marker and no server identity, or `POCKETBASE`, using canonical endpoint/server/account/capability identity. Both modes carry a positive boundary epoch.

## Soft Deletes

User deletes are soft deletes for synced durable entities. Rows are marked `isDeleted = true` and `isSynced = false` so the delete can propagate to PocketBase as a tombstone.

Never-synced local tombstones without a PocketBase id may be hard-deleted locally. Normal synced deletes remain tombstones. The only remote hard-delete path is the explicitly confirmed local-authoritative replacement described below.

## PocketBase Sync

Sync is collection-based and runs through `SyncService` only with an authenticated PocketBase binding. Local-only operations never initialize the provider or report network-refresh success. Each adapter pulls owner-scoped remote records before pushing local unsynced rows. The current dependency order is:

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

Fresh or unbound storage can be adopted into a durable local-only binding without token or server access. A local binding combined with a PocketBase token, or a remote binding without its required token, fails closed. Task navigation is created for either a valid local-only session or an authenticated PocketBase session. Both use the same account/epoch checks for foreground writes, reminders, callbacks, widgets, imports, attachments, and local maintenance; network sync remains strictly authenticated-only.

Signed-out PocketBase login authenticates a detached client and validates capability plus complete owner-scoped inventory before activation. A connectivity-only refresh failure may use a previously proven remote cache offline; authentication or binding failure keeps task UI hidden.

Every account/cache transition and account-owned write runs through the single process-wide `AccountMutationGate`. Account switch and logout refresh and fully synchronize the source online, require zero unsynced rows, invalidate account-bound platform work, and replace the one local cache. Local clear uses durable `PRE_RESET` and `FILES_PENDING` phases so Room reset and attachment cleanup resume independently after failure or process death.

## Local-Authoritative PocketBase Replacement

Connecting from Local only mode is a destructive replacement, not a merge. Detached preflight requires `capabilityVersion = 2` and `authoritativeReplaceVersion = 1`, then shows only sanitized per-collection active/tombstone counts, attachment count, endpoint, account identity, and opaque inventory fingerprints. Passwords and payload data are not retained or displayed.

Confirmation re-inventories the complete local snapshot and the complete destination-owner rows under `AccountMutationGate`. If either opaque fingerprint or the endpoint/server/account boundary changed, the refreshed preview is returned and no durable transition or remote mutation occurs. A matching confirmation durably records the destination binding and `LOCAL_AUTHORITATIVE_REPLACEMENT/REMOTE_DELETE_PENDING` before any remote delete.

Recovery then:

1. Deletes only the authenticated owner's rows in reverse dependency order: task tags, attachments, tasks, tags, categories, notes, countdowns.
2. Requires the complete owner-scoped inventory to be empty.
3. Atomically clears every local PocketBase ID/sync acknowledgement while retaining task content and local attachment files.
4. Exact-seeds the complete local snapshot in normal dependency order and verifies final active/tombstone inventory equality.
5. Promotes and activates the PocketBase session only after exact verification.

The transition is the crash authority and cannot be cancelled after confirmation. A delete, seed, process-death, or concurrent-remote-write failure keeps task UI unmounted. Retry re-inventories, fully deletes the destination owner again, resets all local sync metadata again, and reseeds; it never pulls unexpected destination data into the authoritative local snapshot. Other owners are excluded by PocketBase rules and by the structured owner-scoped gateway.

## Attachment Files

Attachment metadata syncs as an owner-scoped PocketBase record. Attachment binary content uses a protected PocketBase file field and short-lived file tokens. Local optimized files and thumbnails are stored outside Room and referenced by path.

See [Attachments](attachments.md) for image policy, sync states, and file-specific behavior.

## Platform Credential Storage

Android stores tokens behind an Android Keystore AES/GCM key, iOS uses a device-only Keychain item, and macOS desktop uses the login keychain. Windows/Linux use an owner-only application file when supported and display a weaker-storage warning. Passwords are never persisted.

## Related Docs

- [Attachments](attachments.md)
- [Import and Export](import-export.md)
- [Settings](../settings.md)
