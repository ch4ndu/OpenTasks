# PocketBase multi-user ownership cutover

This runbook is the operator contract for migration `011_add_account_ownership.js`.
It converts the existing OpenTasks PocketBase data set from installation-global
ownership to two pre-created authenticated users. It is a production change,
not a client migration. Keep all old clients frozen until the cutover has
passed the verification gates and the owner-aware client rollout is ready.

The migration is transactional, but it is intentionally irreversible. A
rollback after this migration has run is a restore of the complete pre-cutover
PocketBase database and file storage backup. Do not use `migrate down` as a
rollback mechanism.

## 1. Fixed contract and exact inputs

Run this procedure with the exact PocketBase server version currently used by
the production installation: **PocketBase v0.36.7**. Do not use a floating `latest` download
for the production migration or for the disposable proof. Keep the binary,
`pb_migrations/001` through `011`, and the `pb_data` directory together for
each proof or deployment.

The migration accepts exactly these three environment variables. There are no
defaults, aliases, interactive fallbacks, or automatic endpoint fixes:

```bash
export OPENTASKS_ACCOUNT_A_ID='15-character-users-record-id'
export OPENTASKS_ACCOUNT_B_ID='15-character-users-record-id'
export OPENTASKS_LEGACY_ENDPOINT='https://tasks.example.com'
```

The input contract is fail-closed:

- `OPENTASKS_ACCOUNT_A_ID` must resolve to an existing record in the `users`
  auth collection.
- `OPENTASKS_ACCOUNT_B_ID` must resolve to a different existing `users` record.
- The `users` collection must contain exactly two records. The migration never
  creates, deletes, or chooses an account.
- `OPENTASKS_LEGACY_ENDPOINT` must already be canonical: lowercase `http` or
  `https`, a host (IPv4, DNS name, or bracketed IPv6), an optional non-default
  port, and no path, query, fragment, user info, whitespace, or trailing slash.
  For example, `https://tasks.example.com` and
  `http://127.0.0.1:8090` are canonical; `HTTPS://TASKS.EXAMPLE.COM/`,
  `https://tasks.example.com/`, and `https://tasks.example.com:443` are not.
- The migration history must contain every repository migration from
  `001_create_collections.js` through
  `010_enforce_sync_lww_and_capability.js`.
- Existing `account` values, if an operator has already added that field
  manually, may be blank or Account A only. Any other owner aborts the
  transaction.

Account A is the sole owner of the legacy synchronized data. Account B is a
pre-created identity and has no synchronized rows after the migration. Do not
use an email address, display name, record ID from another collection, or a
superuser ID in either account variable.

## 2. Preconditions and maintenance freeze

Record the following before touching the production instance:

1. Confirm the exact binary version:

   ```bash
   ./pocketbase --version
   # Expected: 0.36.7
   ```

2. Confirm the applied production migrations remain unchanged. Stage the
   reviewed repository migrations `009`, `010`, and `011` outside the active
   production `pb_migrations` directory until the disposable proof. Do not
   overwrite or edit an applied migration in place, and do not place `011` in
   the active production directory before the production cutover gate.
   The existing 0.36.7 production history also contains the legacy generated
   migrations `002_add_category_updatedAt.js` and
   `1774282995_updated_tasks.js`. Preserve those files in production and in
   every backup. Their effects (`categories.localUpdatedAt` and
   `tasks.endDeadline`) are already folded into the repository's current
   `001_create_collections.js`, so do not add them to the clean repository
   replay chain or attempt to apply them again.
3. Confirm both Account A and Account B were created manually in `users`, and
   record their IDs and login identities in the restricted change ticket. Do
   not put passwords in this repository, shell history, logs, or the ticket.
4. Confirm the legacy endpoint in `OPENTASKS_LEGACY_ENDPOINT` is the endpoint
   represented by the current app configuration and that it is reachable from
   the operator host.
5. Announce a write freeze. Stop all old OpenTasks clients, scheduled sync
   workers, desktop instances, and any other process that can write to these
   seven collections. Old clients do not send `account`, cannot satisfy the
   locked rules, and cannot interpret capability version 2.
6. Stop PocketBase before copying `pb_data`. Never copy the SQLite database or
   local attachment storage while the server is writing.

The seven synchronized collections are exactly:

```text
categories  tags  tasks  attachments  task_tags  notes  countdowns
```

## 3. Pre-cutover backup and evidence

Create an immutable, access-controlled backup containing the complete
`pb_data` directory, the exact production `pb_migrations` directory, and the
exact PocketBase binary. `pb_data` must contain both `data.db` and the local
attachment storage; backing up only the database is not a valid rollback
point. Preserving the production migration directory is also required because
it contains the two additional applied legacy migrations. If production uses
S3 file storage, capture the matching storage snapshot/version as well and
record how it is restored.

The following is an example for local PocketBase storage. Substitute the actual
paths and change-ticket identifier; do not run it while PocketBase is active:

```bash
BACKUP_ROOT='/secure/backups/opentasks'
BACKUP_ID="pre-multi-user-$(date -u +%Y%m%dT%H%M%SZ)"
PB_ROOT='/opt/pocketbase'

mkdir -p "$BACKUP_ROOT/$BACKUP_ID"
tar --xattrs --acls -C "$PB_ROOT" \
  -czf "$BACKUP_ROOT/$BACKUP_ID/pocketbase-cutover.tar.gz" \
  pb_data pb_migrations pocketbase
sha256sum "$BACKUP_ROOT/$BACKUP_ID/pocketbase-cutover.tar.gz" \
  > "$BACKUP_ROOT/$BACKUP_ID/pocketbase-cutover.tar.gz.sha256"
```

Also record the pre-cutover database counts and tombstone counts. The query
below intentionally counts every row, including `isDeleted = true` tombstones:

```bash
sqlite3 "$PB_ROOT/pb_data/data.db" <<'SQL' \
  > "$BACKUP_ROOT/$BACKUP_ID/pre-cutover-counts.tsv"
.mode tabs
SELECT 'categories', COUNT(*), COALESCE(SUM(CASE WHEN isDeleted = 1 THEN 1 ELSE 0 END), 0) FROM categories;
SELECT 'tags', COUNT(*), COALESCE(SUM(CASE WHEN isDeleted = 1 THEN 1 ELSE 0 END), 0) FROM tags;
SELECT 'tasks', COUNT(*), COALESCE(SUM(CASE WHEN isDeleted = 1 THEN 1 ELSE 0 END), 0) FROM tasks;
SELECT 'attachments', COUNT(*), COALESCE(SUM(CASE WHEN isDeleted = 1 THEN 1 ELSE 0 END), 0) FROM attachments;
SELECT 'task_tags', COUNT(*), COALESCE(SUM(CASE WHEN isDeleted = 1 THEN 1 ELSE 0 END), 0) FROM task_tags;
SELECT 'notes', COUNT(*), COALESCE(SUM(CASE WHEN isDeleted = 1 THEN 1 ELSE 0 END), 0) FROM notes;
SELECT 'countdowns', COUNT(*), COALESCE(SUM(CASE WHEN isDeleted = 1 THEN 1 ELSE 0 END), 0) FROM countdowns;
SQL
```

Verify the archive can be listed and extracted into a non-production temporary
directory before proceeding. Keep the original archive and checksum immutable.

## 4. Required disposable-instance proof

The migration must be exercised against a disposable copy of the exact
production `pb_data` and the exact v0.36.7 binary before production. A mocked
HTTP client or a Kotlin unit test does not prove PocketBase collection rules,
relation validation, SQLite indexes, migration history, or protected-file
behavior.

Prepare the disposable copy while the production server is stopped:

```bash
DISPOSABLE_ROOT="$(mktemp -d)"
tar -xzf "$BACKUP_ROOT/$BACKUP_ID/pocketbase-cutover.tar.gz" \
  -C "$DISPOSABLE_ROOT"
REVIEWED_MIGRATIONS_DIR='/path/to/repository/pocketbase/pb_migrations'
cp "$REVIEWED_MIGRATIONS_DIR/009_add_task_recurrence_anchor_completed_at.js" \
  "$DISPOSABLE_ROOT/pb_migrations/"
cp "$REVIEWED_MIGRATIONS_DIR/010_enforce_sync_lww_and_capability.js" \
  "$DISPOSABLE_ROOT/pb_migrations/"
cp "$REVIEWED_MIGRATIONS_DIR/011_add_account_ownership.js" \
  "$DISPOSABLE_ROOT/pb_migrations/"
chmod +x "$DISPOSABLE_ROOT/pocketbase"
```

Run the migration explicitly with the exact inputs. Run it from the disposable
root so the migration directory is the reviewed copy:

```bash
(
  cd "$DISPOSABLE_ROOT"
  OPENTASKS_ACCOUNT_A_ID="$OPENTASKS_ACCOUNT_A_ID" \
  OPENTASKS_ACCOUNT_B_ID="$OPENTASKS_ACCOUNT_B_ID" \
  OPENTASKS_LEGACY_ENDPOINT="$OPENTASKS_LEGACY_ENDPOINT" \
  ./pocketbase migrate up --dir="$DISPOSABLE_ROOT/pb_data"
)
```

If the disposable copy does not already contain the two production users, stop
and create the two test users in the disposable instance before retrying. Do
not weaken the migration or substitute a third user. Restore the disposable
copy from the backup if a failed attempt needs to be repeated.

The proof is successful only when all of the following are recorded:

- migration `011_add_account_ownership.js` applies once and a second `migrate
  up` is a no-op;
- the migration aborts on missing, blank, whitespace-padded, malformed, or
  conflicting Account A/B/endpoint inputs;
- the migration aborts when the users count is not exactly two, the account IDs
  are equal or missing, a required prior migration is missing, a capability
  record is missing/duplicated, or a pre-existing owner is not Account A;
- each of the seven collections retains the exact pre-cutover total row count
  and tombstone count;
- every row in every collection, including tombstones, has Account A ownership;
- each collection has a unique `(account, localId)` index and no longer has a
  global unique `(localId)` index;
- capability metadata is version 2, retains the original non-empty
  `serverInstanceId`, stores Account A in `legacyOwnerAccount`, stores the
  exact canonical endpoint in `legacyEndpoint`, and is authenticated read-only;
- `users` public list/view/create/update/delete are locked while password auth
  still succeeds for both pre-created users;
- anonymous, Account A, and Account B authorization probes pass the matrix in
  Section 6;
- an Account A attachment can be downloaded only with a valid short-lived file
  token, and Account B cannot view its record or download its file;
- JSON and multipart same-owner writes require the owner field and a strictly
  newer `localUpdatedAt`; stale/equal timestamps, owner changes, and delete
  requests are rejected.

Save the disposable server logs, SQL output, API response status/body summaries
with tokens and passwords removed, and the final `pb_data` checksum in the
change evidence. Never save raw `Authorization` headers, file-token URLs, or
password request bodies.

## 5. Production cutover

After the disposable proof is accepted:

1. Reconfirm the write freeze and that the pre-cutover archive checksum matches
   the recorded backup. Copy only the reviewed `009`, `010`, and `011` files
   into the active production migration directory; keep the applied production
   migrations and the two legacy migration files unchanged.
2. Re-run the pre-cutover count query against the stopped production database
   and compare it with `pre-cutover-counts.tsv`. Any change is a stop condition.
3. Confirm the three exact environment variables. Do not use a shell profile or
   `.env` file that contains passwords or tokens.
4. Apply migrations with the exact v0.36.7 binary from the production root:

   ```bash
   OPENTASKS_ACCOUNT_A_ID="$OPENTASKS_ACCOUNT_A_ID" \
   OPENTASKS_ACCOUNT_B_ID="$OPENTASKS_ACCOUNT_B_ID" \
   OPENTASKS_LEGACY_ENDPOINT="$OPENTASKS_LEGACY_ENDPOINT" \
   ./pocketbase migrate up --dir='/opt/pocketbase/pb_data'
   ```

   A non-zero exit, any `OpenTasks migration 011 aborted` message, or any
   unexpected warning is a stop condition. Because PocketBase migrations run
   transactionally, do not manually repair a partial schema; restore the
   backup and investigate.
5. Run the SQL and metadata checks in Section 6 while the server is still
   stopped. Start the exact server only after those checks pass.
6. Run the read-only and authentication probes in Section 6. Use the
   disposable instance for all mutating probes.
7. Keep old clients disabled. Enable only the owner-aware client build after
   it confirms capability version 2 and the Account A owner metadata. Account B
   must see an empty synchronized data set and must never be seeded with
   Account A rows.
8. Remove the maintenance freeze only after the operator and client rollout
   gates are separately recorded.

## 6. Post-migration verification

### 6.1 Database ownership, counts, and tombstones

Run this query against the migrated database and compare the first two numeric
columns for every collection with the saved pre-cutover output:

```bash
sqlite3 "$PB_ROOT/pb_data/data.db" <<'SQL'
.mode tabs
SELECT 'categories', COUNT(*), COALESCE(SUM(CASE WHEN isDeleted = 1 THEN 1 ELSE 0 END), 0), COALESCE(SUM(CASE WHEN account IS NULL OR account = '' THEN 1 ELSE 0 END), 0) FROM categories;
SELECT 'tags', COUNT(*), COALESCE(SUM(CASE WHEN isDeleted = 1 THEN 1 ELSE 0 END), 0), COALESCE(SUM(CASE WHEN account IS NULL OR account = '' THEN 1 ELSE 0 END), 0) FROM tags;
SELECT 'tasks', COUNT(*), COALESCE(SUM(CASE WHEN isDeleted = 1 THEN 1 ELSE 0 END), 0), COALESCE(SUM(CASE WHEN account IS NULL OR account = '' THEN 1 ELSE 0 END), 0) FROM tasks;
SELECT 'attachments', COUNT(*), COALESCE(SUM(CASE WHEN isDeleted = 1 THEN 1 ELSE 0 END), 0), COALESCE(SUM(CASE WHEN account IS NULL OR account = '' THEN 1 ELSE 0 END), 0) FROM attachments;
SELECT 'task_tags', COUNT(*), COALESCE(SUM(CASE WHEN isDeleted = 1 THEN 1 ELSE 0 END), 0), COALESCE(SUM(CASE WHEN account IS NULL OR account = '' THEN 1 ELSE 0 END), 0) FROM task_tags;
SELECT 'notes', COUNT(*), COALESCE(SUM(CASE WHEN isDeleted = 1 THEN 1 ELSE 0 END), 0), COALESCE(SUM(CASE WHEN account IS NULL OR account = '' THEN 1 ELSE 0 END), 0) FROM notes;
SELECT 'countdowns', COUNT(*), COALESCE(SUM(CASE WHEN isDeleted = 1 THEN 1 ELSE 0 END), 0), COALESCE(SUM(CASE WHEN account IS NULL OR account = '' THEN 1 ELSE 0 END), 0) FROM countdowns;
SELECT 'users', COUNT(*), 0, 0 FROM users;
SQL
```

The fourth column must be zero for all seven collections. To prove that all
rows belong to the designated owner, inspect grouped values and confirm that
the only owner is the exact Account A ID:

```bash
sqlite3 "$PB_ROOT/pb_data/data.db" <<'SQL'
SELECT 'categories', account, COUNT(*) FROM categories GROUP BY account;
SELECT 'tags', account, COUNT(*) FROM tags GROUP BY account;
SELECT 'tasks', account, COUNT(*) FROM tasks GROUP BY account;
SELECT 'attachments', account, COUNT(*) FROM attachments GROUP BY account;
SELECT 'task_tags', account, COUNT(*) FROM task_tags GROUP BY account;
SELECT 'notes', account, COUNT(*) FROM notes GROUP BY account;
SELECT 'countdowns', account, COUNT(*) FROM countdowns GROUP BY account;
SQL
```

Confirm the tombstone counts did not decrease. A tombstone is data and must
remain owned by Account A; do not clean up tombstones as part of this change.

### 6.2 Composite indexes and schema rules

For each of the seven tables, verify `PRAGMA index_list` contains the named
unique composite index and does not contain the old global unique local-ID
index:

```text
idx_categories_account_localId
idx_tags_account_localId
idx_tasks_account_localId
idx_attachments_account_localId
idx_task_tags_account_localId
idx_notes_account_localId
idx_countdowns_account_localId
```

For each index, `PRAGMA index_info('<index-name>')` must report the columns in
this order: `account`, then `localId`. A global unique index on only `localId`
is a failed migration because it prevents the two accounts from sharing the
stable Inbox local ID.

Inspect the collection definitions through the superuser dashboard/API or the
disposable instance and record these rules for every sync collection:

```text
list:   @request.auth.id != "" && account = @request.auth.id
view:   @request.auth.id != "" && account = @request.auth.id
create: @request.auth.id != "" && @request.body.account = @request.auth.id
update: @request.auth.id != "" && account = @request.auth.id
        && @request.body.account = account
        && (@request.body.localUpdatedAt > localUpdatedAt)
delete: locked (null rule; tombstones only)
```

`attachments.file` must be a single protected file field. `users` must have
null/locked list, view, create, update, and delete rules without changing its
password-authentication options. `opentasks_sync_meta` must be authenticated
read-only: authenticated list/view, locked create/update/delete.

PocketBase v0.36.7 supports the relation-field `:changed` modifier. This
migration intentionally retains the stricter explicit equality wire contract:
JSON and multipart updates must always submit `account`, and it must equal both
the stored owner (through the equality clause) and the authenticated user
(through the stored-record owner clause). An omitted or different account must
be rejected. The 0.36.7 real-server matrix verifies all three cases.

Verify metadata directly:

```sql
SELECT capabilityVersion, serverInstanceId, legacyOwnerAccount, legacyEndpoint
FROM opentasks_sync_meta;
```

There must be exactly one row, `capabilityVersion = 2`, a non-empty unchanged
`serverInstanceId`, `legacyOwnerAccount = OPENTASKS_ACCOUNT_A_ID`, and an exact
match to the canonical `OPENTASKS_LEGACY_ENDPOINT`.

### 6.3 Authorization matrix

Use the real Account A/B credentials only for non-mutating production probes.
Use the disposable instance for create/update/multipart/delete probes. PocketBase
auth tokens are sent as `Authorization: TOKEN` (without a `Bearer` prefix).

Authenticate through the password endpoint using a short-lived secure request
body, then remove that body immediately:

```text
POST /api/collections/users/auth-with-password
{"identity":"ACCOUNT_EMAIL","password":"PASSWORD"}
```

Do not print the response token. The required outcomes are:

| Probe | Anonymous | Account A | Account B |
|---|---|---|---|
| list any sync collection | no rows; no data leak | only A rows | empty initially; only B rows later |
| view an A-owned record | denied/not found | allowed | denied/not found |
| view the capability record | denied | allowed | allowed |
| list/view/create/update/delete `users` | locked | locked | locked |
| download an A-owned attachment file | denied without valid file token | allowed with A file token | denied with B token |

For list rules, an unauthorized PocketBase list request can return an empty
successful page rather than a useful HTTP error. Inspect the body and verify
that no records are returned; a status code alone is not sufficient.

On the disposable instance, also prove the write boundary:

- Account A creates a row with `account = A`; it succeeds.
- Account B cannot create a row with `account = A`; it is rejected.
- Account B creates a row with `account = B`; it succeeds and can be viewed by
  B but not by A.
- Account A updates an A row with `account = A` and a strictly newer
  `localUpdatedAt`; it succeeds.
- An owner-changing update, an omitted/wrong owner update, a stale/equal
  timestamp update, and an Account B update of an A row are rejected.
- JSON and attachment multipart updates both include and preserve the owner
  relation. A multipart request must not bypass the LWW or ownership rules.
- A delete request is locked. A tombstone is created/updated through the
  normal owner-scoped update path with `isDeleted = true`.

For protected-file proof, create an A-owned attachment with a harmless image on
the disposable instance, obtain a short-lived file token from
`POST /api/files/token`, and check:

1. the A token downloads the file;
2. no token, an expired token, and a B token do not download it;
3. B cannot view the attachment record; and
4. the response body and logs contain no file token or raw authorization value.

### 6.4 Capability and client rollout gate

The new client may activate only when its detached capability read confirms:

```text
capabilityVersion = 2
legacyOwnerAccount = Account A ID
legacyEndpoint = exact canonical endpoint
serverInstanceId = non-empty stable value
```

The old client rollout remains blocked. This server migration alone does not
implement client authentication, cache binding, owner-scoped sync, or account
switching; those are later plan phases.

## 7. Rollback and restore contract

### When rollback is required

Rollback is required for a failed migration, failed post-migration count/rule
probe, data corruption, or an incident in which any account can see another
account's record/file. Stop all clients immediately and preserve logs and the
post-cutover database as a separate incident artifact before restoring.

### Restore procedure

1. Keep the write freeze in place and stop PocketBase.
2. Preserve the current `pb_data` directory under an incident-specific name;
   do not delete it.
3. Verify the checksum of the pre-cutover `pocketbase-cutover.tar.gz` archive.
4. Move the failed/post-cutover `pb_data`, `pb_migrations`, and binary aside as
   incident artifacts, then extract the archive so the matching database,
   local attachment storage, production migration history, and v0.36.7 binary
   are restored together. If storage is S3-backed, restore the matching storage
   snapshot/version before restarting.
5. Confirm the restored active migration directory does not contain `009`,
   `010`, or `011` unless it contained them when the backup was made. Leaving
   an unapplied cutover migration in the active directory could cause it to run
   again. Do not set the Account A/B inputs during rollback.
6. Verify the pre-cutover schema, counts, tombstone counts, and public-rule
   behavior against the saved evidence.
7. Keep the owner-aware client disabled and notify the release owner before
   allowing frozen legacy clients to resume.

Never:

- run migration 011's down function as a rollback;
- restore `data.db` without the matching attachment storage;
- delete the only post-cutover copy before incident review;
- restart a restored pre-cutover database with migration 011 available; or
- allow old and owner-aware clients to write concurrently during recovery.

A rollback loses any Account B records written after the pre-cutover backup by
design. Record that loss explicitly in the incident report and repeat the full
disposable proof before a later cutover attempt.

## 8. Evidence checklist

Attach these artifacts to the change record, with secrets and tokens removed:

- exact PocketBase v0.36.7 version output;
- the reviewed migration file hashes for `001` through `011`;
- pre-cutover archive checksum and proof that the database, storage, migration
  directory, and exact binary extracted;
- pre- and post-cutover seven-collection total/tombstone count output;
- zero-unowned-row and grouped-owner output;
- all seven composite-index checks;
- capability metadata and locked-rule inspection;
- disposable authorization, LWW, JSON/multipart, tombstone, and protected-file
  probe summaries;
- production read-only Account A/B/anonymous probe summaries; and
- rollout or rollback decision, operator, timestamp, and preserved backup path.

The migration is not considered verified until the disposable real-instance
proof and the production count/rule checks are both attached. The absence of a
local PocketBase binary, disposable data copy, or credentials is an unresolved
environmental check—not permission to skip the proof or to mark this phase
complete.
