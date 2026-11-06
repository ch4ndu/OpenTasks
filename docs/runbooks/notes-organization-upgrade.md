# Notes organization upgrade

This change adds Room schema 17 and four fields to the existing owner-scoped PocketBase `notes` collection. It does not deploy the server migration automatically.

1. Back up the server database and client caches. Complete or recover any pending account replacement or seeding transition.
2. Stop older synced clients. Apply `015_add_note_organization.js` after migration 014 using the existing PocketBase migration procedure.
3. Update all synced clients before reopening them. Capability version remains 2, following the existing coordinated rollout convention. Strict payload validation rejects missing or unknown note fields; mixed versions are unsupported.
4. Local-only clients need only the automatic, non-destructive Room migration 16 → 17. Existing notes remain active, unpinned, untagged, and without outgoing note links.
5. On the intended server, verify pin/archive and restore, tags, link/unlink, target rename/deletion, and clearing all metadata across two upgraded clients. Verify owner isolation and account-replacement/seed inventory preservation before release.

`tagIds` and `linkedNoteIds` are JSON arrays in PocketBase text fields. Empty migrated text fields normalize to empty arrays. Explicit empty arrays and false flags must be sent when clearing values. Missing keys, malformed arrays, duplicate/blank/non-string IDs, and nulls fail validation. References resolve only against the active account cache; deleting a target does not delete the referring note. All note fields share existing whole-record last-write-wins semantics, so concurrent organization edits are not independently merged.

The down migration intentionally refuses to discard note organization. Roll back using coordinated server/client backups, preserving newer data first. Host tests and compilation do not establish live multi-device deployment success.
