# Notes

## Overview

Notes provide a lightweight rich-text writing area separate from tasks. Each note has a title and rich content. Notes can be created, edited, deleted, persisted locally, and synced through PocketBase.

## User Flow

Users open the Notes tab, create a note from the floating action button, edit an existing note from the list, or delete a note from the edit sheet. Note content uses the same rich editor family used for task descriptions.

That flow remains available in Classic. Reimagined adds an on-demand library search opened by a toolbar icon, quick capture into a draft, and a full writing workspace. Settings is the rightmost library toolbar action and is absent from the note details toolbar. Expanded windows show the note library beside the editor; focus mode hides that library and linked-task area. Save keeps the workspace open. Switching notes or closing a changed draft requires Discard or Keep editing, and note mutations block switching until their result is known. Save and open linked tasks also works for a newly created note, using its committed ID. Text-edit Undo/Redo remains part of the rich editor; there is no task-completion or deletion Undo.

Ordinary writing keeps app navigation available; focus mode hides it. Leaving through a tab or Settings uses the same dirty-draft and mutation guard as closing the editor. A note opened from a task retains that source task's editor entry and draft.

After a failed save, recreating the host preserves both the unsaved draft and the last successful save baseline, so leaving still requires a discard decision. Hidden workspace panels stop observing their data, and the covered Notes library resumes observation when the editor closes while retaining its search and scroll state.

If an open note disappears during sync, both Classic and Reimagined keep the draft available and offer Save as new or Discard. Save as new creates a new note ID and leaves the original deletion intact. Back requires an explicit discard decision while the note is missing, and the missing editor can remount its saved draft after host recreation, including when its first completed lookup is already absent. Initial loading is distinct from that absent result; leaving during that lookup also requires explicit discard, without replacing the saved draft with an empty editor. A discarded restored draft cannot populate another note or a new editor request. Both editors keep mutation failures visible inside the editor until retry or another explicit action. Repository writes recheck that the original note is still active under the mutation gate, so a queued stale save cannot resurrect a deleted row.

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

In the Reimagined library, system Back closes active search and clears its query before navigating away. Linked-task counts and the editor list share one task observation stream.
