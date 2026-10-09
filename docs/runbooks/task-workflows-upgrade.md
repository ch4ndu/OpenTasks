# Task workflows upgrade

This change adds Room schema 16, `tasks.sourceNoteId`, and the independent owner-scoped `task_completions` PocketBase collection. It does not deploy the server migration automatically.

## Rollout

1. Back up the PocketBase database and file storage. Finish or recover any account replacement/server-seeding transition before starting this upgrade.
2. Stop all older synced clients. Migration 014 changes the complete task payload and the account inventory; mixed old/new clients are unsupported. In particular, never run account replacement with an older client after this migration.
3. Apply `014_add_task_workflows.js` after migrations 011–013 using the existing PocketBase migration procedure. It retains task data, adds a nullable-by-empty source note ID, and creates a protected history collection. Capability version remains 2, matching the existing schema-rollout convention.
4. Update every synced client before reopening it. Local-only users require no server migration. Room migration 15 → 16 preserves the existing cache and seeds known completed tasks into history.
5. Verify ordinary completion, recurring occurrence advancement, sync to a second upgraded client, reopening/deletion with retained history, note-link round trips, and owner isolation. Confirm the account-replacement preview includes completion history before using replacement.

## Data semantics

History is a snapshot of completion events, not an undo log. Reopening a task keeps the old entry; completing it again creates another. A recurring occurrence has one deterministic task/occurrence identity. The same occurrence completed concurrently on two devices converges through existing timestamp-based sync; its snapshot is subject to the app's existing last-write-wins policy.

Ordinary completion events have independent IDs. Completing the same non-recurring task on two offline devices can therefore retain two entries after sync. A recurring occurrence whose identity already exists locally does not create another entry, even if its history row is a tombstone or the task's deadline was moved back to that occurrence.

Known DONE timestamps are backfilled during local migration. Older recurring occurrences, deleted tasks, and completions without a recorded timestamp are not reconstructed. CSV/ICS remain task interchange formats and do not export logbook history or note links.

CSV import retains its existing completion-time fallback: a completed row with no supplied completion timestamp receives the import time. The logbook records that value, so such imports appear as recent completions; they are not reconstructed historical completion dates.

Normal task or note deletion does not delete history. Clearing an account cache clears local history; optional sync restores that owner's history on the next authenticated initial pull. Confirmed local-authoritative replacement intentionally replaces the destination owner's history along with all other synchronized content.

## Rollback and validation boundary

Migration 014 deliberately has no destructive down migration. Roll back using coordinated server and client/cache backups, preserving newer history first if needed. A schema smoke check on a disposable PocketBase 0.36.7 database is not evidence of a live multi-device rollout; perform step 5 against the intended upgraded server before release.
