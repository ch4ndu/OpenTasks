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

The image policy currently uses a 1600 px maximum long edge, 320 px thumbnails, quality 80, and a 5 MB upload cap.

## Sync Behavior

Attachment sync uses `AttachmentSyncAdapter` and the PocketBase `attachments` collection. Push uses multipart file upload through the PocketBase Kotlin SDK. Pull downloads the remote file from `/api/files/attachments/{recordId}/{filename}` and stores a local optimized copy plus thumbnail.

Attachment uploads are gated on parent task sync. A task attachment is not uploaded until its task has a PocketBase id.

Sync states:

- `LOCAL_ONLY`: local file exists and needs upload.
- `SYNCED`: metadata and file are synced.
- `NEEDS_DOWNLOAD`: remote record exists but local file needs download.
- `FAILED`: retryable sync or download failure.
- `BLOCKED`: non-retryable policy or decode failure.

Deletes are tombstones. Deleting an image marks the attachment deleted and removes local files where practical; sync clears or updates the remote file record rather than treating normal app deletion as a hard server delete.

## PocketBase

PocketBase requires the `attachments` collection created by `pocketbase/pb_migrations/008_create_attachments.js`. The collection contains owner fields, metadata fields, local timestamps, and a single `file` field with a 5 MB max size and image MIME type allowlist.

Files follow the app's current public sync model: collection rules are public, and file URLs are public-by-randomized-name rather than account-protected.

## Related Docs

- [Tasks](../tasks.md) for the current task image UI.
- [Sync and Storage](sync-and-storage.md) for the broader sync model and migrations.

## Current Limitations

The attachment model is generic, but only task images are wired today. Adding attachments to notes, countdowns, or other owners requires owner-specific UI, use cases/actions, and any needed row affordances.
