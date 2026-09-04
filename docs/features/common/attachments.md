# Attachments

## Overview

Attachments are designed as owner-based shared infrastructure. Each attachment records an `ownerType`, `ownerId`, and `kind`, which allows the same table and sync collection to support multiple feature owners over time.

The current implemented feature is task image attachments:

- `ownerType = "task"`
- `kind = "image"`
- Multiple ordered images per task.
- Local-first storage with thumbnails.
- Background PocketBase upload/download.

Notes, countdowns, and other owners do not currently have attachment UI or domain actions.

## User Flow

In the task editor, users can add pending images from the platform picker or camera where available. For new tasks, the app saves the task first and then saves pending images against the new task id. For existing tasks, pending images are attached after the task update completes.

Existing task images appear as thumbnails in the editor and as lightweight image indicators in task list and matrix rows. Users can open an existing image in a full-screen viewer and delete it.

If image save fails after a new task is created, the task remains saved and failed pending images are handed off to the edit flow so the user can retry instead of losing the selection.

## Technical Design

The local model is `Attachment` in the `attachments` Room table. Important fields include owner identity, local image path, thumbnail path, remote file name, MIME type, dimensions, sort order, sync state, PocketBase id, and soft-delete flags.

Domain actions currently include `AddTaskImageAction` and `RemoveTaskImageAction`. Read use cases include `ObserveTaskImagesUseCase` for editor images and `ObserveTaskImageSummariesUseCase` for task row affordances.

Platform storage is abstracted by `AttachmentFileStorage`:

- Android stores optimized image files under app files, respects EXIF orientation, uses WebP on Android 11+, and falls back to JPEG.
- iOS stores optimized JPEG files under the app documents attachment directory.
- JVM stores optimized and thumbnail files under `~/.opentasks/attachments/images`, using Skia to resize and encode WebP with JPEG fallback.

Image intake rejects sources larger than 32 MiB, any source dimension above 16,384 px, or more than 64 million decoded pixels before allocating the full decoded image. Accepted images are optimized to a 1600 px maximum long edge with 320 px thumbnails, quality 80, and a 5 MiB upload cap.

iOS gallery selection uses one-image `PHPicker` file representations. The temporary representation is copied through a 32 MiB-plus-one bounded native read while the provider callback owns its URL; ImageIO then validates metadata and applies orientation while producing the stored 1600 px image and 320 px thumbnail. Camera capture retains the camera-specific `UIImage` preprocessing path.

Each allocated image/thumbnail pair is recorded in the account-owned `attachment_file_cleanup` table before the first file write. A file lease is released only after an exact attachment-row read proves the path is referenced, or after deletion proves the path is absent. Failed, cancelled, losing-download, and superseded-file paths therefore remain durably retryable across process death.

## Sync Behavior

Attachment sync uses `AttachmentSyncAdapter` and the PocketBase `attachments` collection. Push uses the structured owner-scoped multipart gateway. Pull obtains a short-lived PocketBase file token, downloads the protected file, and stores a local optimized copy plus thumbnail. Confirmed token rejection refreshes and retries once.

Attachment uploads are gated on parent task sync. A task attachment is not uploaded until its task has a PocketBase id.

Sync states:

- `LOCAL_ONLY`: local file exists and needs upload.
- `SYNCED`: metadata and file are synced.
- `NEEDS_DOWNLOAD`: remote record exists but local file needs download.
- `FAILED`: retryable sync or download failure.
- `BLOCKED`: non-retryable policy or decode failure.

Normal deletes are tombstones. Deleting an image marks the attachment deleted; the shared cleanup owner retains both paths until both files are proven absent, then conditionally clears the unchanged tombstone paths. Sync clears or updates the remote file record rather than treating normal app deletion as a hard server delete. The sole remote hard-delete exception is a confirmed local-authoritative account replacement: the owner-scoped executor deletes the destination attachment record/file, preserves the local attachment bytes, then uploads them during exact reseeding.

## PocketBase

PocketBase requires the `attachments` collection created by `pocketbase/pb_migrations/008_create_attachments.js`. The collection contains owner fields, metadata fields, local timestamps, and a single `file` field with a 5 MB max size and image MIME type allowlist.

Migration `011` adds the required account owner relation and protects the file field. Migration `012` permits hard deletion by the authenticated stored owner for the replacement protocol while keeping anonymous and cross-owner requests hidden. Collection rules and the client gateway restrict metadata and file access to the active owner; cross-account raw responses are rejected before local persistence.

## Related Docs

- [Tasks](../tasks.md) for the current task image UI.
- [Sync and Storage](sync-and-storage.md) for the broader sync model and migrations.

## Current Limitations

The attachment model is generic, but only task images are wired today. Adding attachments to notes, countdowns, or other owners requires owner-specific UI, use cases/actions, and any needed row affordances.
