# Notes

## Overview

Notes provide a lightweight rich-text writing area separate from tasks. Each note has a title and rich content. Notes can be created, edited, deleted, persisted locally, and synced through PocketBase.

## User Flow

Users open the Notes tab, create a note from the floating action button, edit an existing note from the list, or delete a note from the edit sheet. Note content uses the same rich editor family used for task descriptions.

## Technical Design

Notes are stored in the `notes` Room table. `NoteRepositoryImpl` wraps `NoteDao`, applies timestamp conversion, soft-deletes notes, and triggers sync after writes.

`NoteViewModel` observes all notes through `ObserveAllNotesUseCase`, observes a selected note through `ObserveNoteByIdUseCase`, and writes through `AddNoteAction`, `UpdateNoteAction`, and `DeleteNoteAction`.

PocketBase sync uses `NoteSyncAdapter` and `NoteRecord`. Notes are synced as text records, not as file attachments.

## Shared Capabilities

- [Sync and Storage](common/sync-and-storage.md) for Room, timestamps, soft deletes, and PocketBase sync.
- [Attachments](common/attachments.md) for the shared attachment direction. Notes do not currently expose attachments.

## Current Limitations

Notes do not currently support attachments, reminders, tags, categories, or calendar dates.
