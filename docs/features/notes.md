# Notes

## Overview

Notes provide a lightweight rich-text writing area separate from tasks. Each note has a title and rich content. Notes can be created, edited, deleted, persisted locally, and synced through PocketBase.

## User Flow

Users open the Notes tab, create a note from the floating action button, edit an existing note from the list, or delete a note from the edit sheet. Note content uses the same rich editor family used for task descriptions.

## Linked tasks

Existing notes expose Save and open linked tasks. This saves the current draft before navigation and keeps save failures visible in the editor. The linked-tasks view supports opening tasks, creating a task with the note title, and returning to the note. Tasks store a synced `sourceNoteId`; the note does not contain a second copy of the relationship. Deleting a note leaves its tasks intact. See [Tasks](tasks.md#tasks-linked-to-notes) for the full behavior.

## Technical Design

Notes are stored in the `notes` Room table. `NoteRepositoryImpl` wraps `NoteDao`, applies timestamp conversion, soft-deletes notes, and triggers sync after writes.

`NoteViewModel` observes all notes through `ObserveAllNotesUseCase`, observes a selected note through `ObserveNoteByIdUseCase`, and writes through `AddNoteAction`, `UpdateNoteAction`, and `DeleteNoteAction`.

PocketBase sync uses `NoteSyncAdapter` and `NoteRecord`. Notes are synced as text records, not as file attachments.

## Shared Capabilities

- [Sync and Storage](common/sync-and-storage.md) for Room, timestamps, soft deletes, and PocketBase sync.
- [Attachments](common/attachments.md) for the shared attachment direction. Notes do not currently expose attachments.

## Current Limitations

Notes do not currently support attachments, reminders, tags, categories, or calendar dates.
