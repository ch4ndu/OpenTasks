/// <reference path="../pb_data/types.d.ts" />

/**
 * OpenTasks PocketBase migration — assigns the existing data set to Account A
 * and changes the sync collections to authenticated, owner-scoped access.
 *
 * This migration is intentionally operator-input driven and fail-closed. It
 * must be run with the exact three environment variables documented in
 * docs/runbooks/pocketbase-multi-user-cutover.md. Do not run it against a
 * disposable or production instance without first taking the database and
 * attachment-storage backup required by that runbook.
 */

const SYNC_COLLECTIONS = [
    "categories",
    "tags",
    "tasks",
    "attachments",
    "task_tags",
    "notes",
    "countdowns",
]

const REQUIRED_MIGRATIONS = [
    "001_create_collections.js",
    "002_create_tags_countdowns.js",
    "003_add_isStarred_to_tasks.js",
    "004_add_section_to_tasks.js",
    "005_replace_isCompleted_with_status.js",
    "006_create_task_tags.js",
    "007_add_subtasks_to_tasks.js",
    "008_create_attachments.js",
    "009_add_task_recurrence_anchor_completed_at.js",
    "010_enforce_sync_lww_and_capability.js",
]

const LEGACY_LWW_UPDATE_RULE = "@request.body.localUpdatedAt > localUpdatedAt"
const AUTHENTICATED_RULE = '@request.auth.id != ""'

function fail(message) {
    throw new Error(`OpenTasks migration 011 aborted: ${message}`)
}

function requiredInput(name) {
    const value = $os.getenv(name)
    if (typeof value !== "string" || value.length === 0 || value !== value.trim()) {
        fail(`${name} must be supplied exactly; no default or whitespace is accepted`)
    }
    return value
}

function accountInput(name) {
    const value = requiredInput(name)
    if (/[\s'"\\]/.test(value)) {
        fail(`${name} is not a valid PocketBase user id`)
    }
    return value
}

/**
 * Canonical endpoint format used by the cutover metadata:
 *   scheme://lowercase-host[:non-default-port]
 * There is no path, query, fragment, user info, trailing slash, or default
 * port. The input must already be in this form; it is never silently fixed.
 */
function canonicalEndpointInput() {
    const value = requiredInput("OPENTASKS_LEGACY_ENDPOINT")
    const match = value.match(/^(https?):\/\/(\[[0-9A-Fa-f:.]+\]|[A-Za-z0-9.-]+)(?::([0-9]+))?$/)
    if (!match) {
        fail("OPENTASKS_LEGACY_ENDPOINT must be a canonical http(s) URL without a path, query, fragment, or trailing slash")
    }

    const protocol = match[1].toLowerCase()
    const host = match[2].toLowerCase()
    const port = match[3] ? Number(match[3]) : (protocol === "https" ? 443 : 80)
    if (!Number.isInteger(port) || port < 1 || port > 65535) {
        fail("OPENTASKS_LEGACY_ENDPOINT contains an invalid port")
    }

    const defaultPort = protocol === "https" ? 443 : 80
    const canonical = `${protocol}://${host}${port === defaultPort ? "" : `:${port}`}`
    if (canonical !== value) {
        fail("OPENTASKS_LEGACY_ENDPOINT is not already in canonical form")
    }
    return canonical
}

function requireCollection(app, name) {
    try {
        return app.findCollectionByNameOrId(name)
    } catch (error) {
        fail(`required collection ${name} is missing`)
    }
}

function requireUsersCollection(app) {
    const users = requireCollection(app, "users")
    if (users.type !== "auth") {
        fail("users must remain a PocketBase auth collection")
    }
    return users
}

function requireUser(app, users, id, label) {
    try {
        const record = app.findRecordById(users, id)
        if (record.id !== id) {
            fail(`${label} does not resolve to the supplied user id`)
        }
        return record
    } catch (error) {
        fail(`${label} must be an existing users record`)
    }
}

function migrationIsApplied(app, file) {
    const result = new DynamicModel({ count: 0 })
    app.db()
        .newQuery("SELECT COUNT(*) AS count FROM _migrations WHERE file = {:file}")
        .bind({ file })
        .one(result)
    return Number(result.count) === 1
}

function requirePreviousMigrations(app) {
    for (const file of REQUIRED_MIGRATIONS) {
        if (!migrationIsApplied(app, file)) {
            fail(`migration history is missing ${file}; restore the pre-cutover backup and apply migrations 001 through 010 first`)
        }
    }
}

function countRows(app, tableName, whereClause, params) {
    const result = new DynamicModel({ count: 0 })
    let query = `SELECT COUNT(*) AS count FROM ${tableName}`
    if (whereClause) {
        query += ` WHERE ${whereClause}`
    }
    const statement = app.db().newQuery(query)
    if (params) {
        statement.bind(params)
    }
    statement.one(result)
    return Number(result.count)
}

function validateExistingAccountField(collection, users, app, accountAId) {
    const field = collection.fields.getByName("account")
    if (!field) {
        return null
    }
    if (field.type() !== "relation" || field.collectionId !== users.id || field.maxSelect > 1) {
        fail(`${collection.name}.account already exists with an incompatible schema`)
    }

    const conflictingRows = countRows(
        app,
        collection.name,
        "account IS NOT NULL AND account != '' AND account != {:account}",
        { account: accountAId },
    )
    if (conflictingRows !== 0) {
        fail(`${collection.name} already contains ownership different from Account A`)
    }
    return field
}

function addNullableAccountField(app, collection, users, accountAId) {
    const existingField = validateExistingAccountField(collection, users, app, accountAId)
    if (existingField) {
        existingField.required = false
    } else {
        collection.fields.add(new RelationField({
            name: "account",
            collectionId: users.id,
            maxSelect: 1,
            required: false,
            cascadeDelete: false,
        }))
    }
    app.save(collection)
}

function backfillAndVerifyOwnership(app, collectionName, snapshot, accountAId) {
    const currentCount = countRows(app, collectionName)
    if (currentCount !== snapshot.rowCount) {
        fail(`${collectionName} row count changed during ownership preparation`)
    }

    app.db()
        .newQuery(`UPDATE ${collectionName} SET account = {:account} WHERE account IS NULL OR account = ''`)
        .bind({ account: accountAId })
        .execute()

    const unownedCount = countRows(
        app,
        collectionName,
        "account IS NULL OR account = '' OR account != {:account}",
        { account: accountAId },
    )
    if (unownedCount !== 0) {
        fail(`${collectionName} still contains ${unownedCount} unowned or mismatched rows after backfill`)
    }

    const afterCount = countRows(app, collectionName)
    if (afterCount !== snapshot.rowCount) {
        fail(`${collectionName} row count changed during ownership backfill`)
    }

    const afterTombstoneCount = countRows(app, collectionName, "isDeleted = true")
    if (afterTombstoneCount !== snapshot.tombstoneCount) {
        fail(`${collectionName} tombstone count changed during ownership backfill`)
    }
}

function replaceLocalIdIndex(collection) {
    const compositeIndex = `CREATE UNIQUE INDEX idx_${collection.name}_account_localId ON ${collection.name} (account, localId)`
    collection.indexes = collection.indexes.filter((index) => {
        return !/CREATE\s+UNIQUE\s+INDEX[\s\S]*\(\s*localId\s*\)/i.test(index)
    })
    if (!collection.indexes.includes(compositeIndex)) {
        collection.indexes.push(compositeIndex)
    }
}

function configureOwnedCollection(app, collection) {
    if (String(collection.updateRule) !== LEGACY_LWW_UPDATE_RULE) {
        fail(`${collection.name} does not have the migration 010 LWW update rule; refusing to replace it`)
    }

    const accountField = collection.fields.getByName("account")
    if (!accountField) {
        fail(`${collection.name}.account is missing after backfill`)
    }
    accountField.required = true

    replaceLocalIdIndex(collection)

    const ownerRule = `${AUTHENTICATED_RULE} && account = @request.auth.id`
    collection.listRule = ownerRule
    collection.viewRule = ownerRule
    collection.createRule = `${AUTHENTICATED_RULE} && @request.body.account = @request.auth.id`
    // Require every client update to submit the existing owner explicitly.
    // PocketBase 0.36.7 supports :changed, but equality is the intentional wire
    // contract because the client always injects account and the real-server
    // authorization matrix verifies omitted, changed, and cross-owner denial.
    collection.updateRule = `${ownerRule} && @request.body.account = account && (${LEGACY_LWW_UPDATE_RULE})`
    collection.deleteRule = null

    if (collection.name === "attachments") {
        const fileField = collection.fields.getByName("file")
        if (!fileField || fileField.type() !== "file") {
            fail("attachments.file is missing or is not a file field")
        }
        fileField.protected = true
    }

    app.save(collection)
}

function validateOrAddMetaField(meta, fieldName, fieldDefinition, users) {
    const field = meta.fields.getByName(fieldName)
    if (!field) {
        meta.fields.add(fieldDefinition)
        return
    }

    if (fieldName === "legacyOwnerAccount") {
        if (field.type() !== "relation" || field.collectionId !== users.id || field.maxSelect > 1) {
            fail("opentasks_sync_meta.legacyOwnerAccount already exists with an incompatible schema")
        }
    } else if (field.type() !== "text") {
        fail("opentasks_sync_meta.legacyEndpoint already exists with an incompatible schema")
    }
    field.required = false
}

function configureCapabilityMetadata(app, users, accountAId, canonicalEndpoint) {
    const meta = requireCollection(app, "opentasks_sync_meta")
    const records = app.findAllRecords(meta)
    if (records.length !== 1) {
        fail("opentasks_sync_meta must contain exactly one capability record")
    }

    const record = records[0]
    if (record.getInt("capabilityVersion") !== 1) {
        fail("opentasks_sync_meta is not at capability version 1; refusing an ambiguous upgrade")
    }
    const serverInstanceId = record.getString("serverInstanceId")
    if (serverInstanceId.trim() === "") {
        fail("opentasks_sync_meta.serverInstanceId is blank")
    }

    const existingOwner = record.getString("legacyOwnerAccount")
    if (existingOwner !== "" && existingOwner !== accountAId) {
        fail("opentasks_sync_meta already names an ownership different from Account A")
    }
    const existingEndpoint = record.getString("legacyEndpoint")
    if (existingEndpoint !== "" && existingEndpoint !== canonicalEndpoint) {
        fail("opentasks_sync_meta already names a different legacy endpoint")
    }

    validateOrAddMetaField(meta, "legacyOwnerAccount", new RelationField({
        name: "legacyOwnerAccount",
        collectionId: users.id,
        maxSelect: 1,
        required: false,
        cascadeDelete: false,
    }), users)
    validateOrAddMetaField(meta, "legacyEndpoint", new TextField({
        name: "legacyEndpoint",
        required: false,
    }), users)
    app.save(meta)

    record.set("capabilityVersion", 2)
    record.set("legacyOwnerAccount", accountAId)
    record.set("legacyEndpoint", canonicalEndpoint)
    app.save(record)

    const ownerField = meta.fields.getByName("legacyOwnerAccount")
    const endpointField = meta.fields.getByName("legacyEndpoint")
    ownerField.required = true
    endpointField.required = true
    meta.listRule = AUTHENTICATED_RULE
    meta.viewRule = AUTHENTICATED_RULE
    meta.createRule = null
    meta.updateRule = null
    meta.deleteRule = null
    app.save(meta)

    const verified = app.findAllRecords(meta)
    if (verified.length !== 1 || verified[0].getInt("capabilityVersion") !== 2) {
        fail("opentasks_sync_meta capability version verification failed")
    }
    if (verified[0].getString("legacyOwnerAccount") !== accountAId) {
        fail("opentasks_sync_meta legacy owner verification failed")
    }
    if (verified[0].getString("legacyEndpoint") !== canonicalEndpoint) {
        fail("opentasks_sync_meta legacy endpoint verification failed")
    }
    if (verified[0].getString("serverInstanceId") !== serverInstanceId) {
        fail("opentasks_sync_meta server instance identity changed during migration")
    }
}

migrate(
    // -- UP --
    (app) => {
        requirePreviousMigrations(app)

        const accountAId = accountInput("OPENTASKS_ACCOUNT_A_ID")
        const accountBId = accountInput("OPENTASKS_ACCOUNT_B_ID")
        if (accountAId === accountBId) {
            fail("Account A and Account B must be distinct users")
        }
        const canonicalEndpoint = canonicalEndpointInput()

        const users = requireUsersCollection(app)
        const userRecords = app.findAllRecords(users)
        if (userRecords.length !== 2) {
            fail(`exactly two users are required; found ${userRecords.length}`)
        }
        requireUser(app, users, accountAId, "OPENTASKS_ACCOUNT_A_ID")
        requireUser(app, users, accountBId, "OPENTASKS_ACCOUNT_B_ID")

        const snapshots = {}
        for (const name of SYNC_COLLECTIONS) {
            const collection = requireCollection(app, name)
            if (!collection.fields.getByName("localId") || !collection.fields.getByName("localUpdatedAt")) {
                fail(`${name} is missing sync identity or timestamp fields`)
            }
            if (!collection.fields.getByName("isDeleted")) {
                fail(`${name} is missing its durable tombstone field`)
            }
            snapshots[name] = {
                rowCount: countRows(app, name),
                tombstoneCount: countRows(app, name, "isDeleted = true"),
            }
        }

        for (const name of SYNC_COLLECTIONS) {
            addNullableAccountField(app, requireCollection(app, name), users, accountAId)
        }

        for (const name of SYNC_COLLECTIONS) {
            backfillAndVerifyOwnership(app, name, snapshots[name], accountAId)
        }

        for (const name of SYNC_COLLECTIONS) {
            configureOwnedCollection(app, requireCollection(app, name))
        }

        configureCapabilityMetadata(app, users, accountAId, canonicalEndpoint)

        // Lock public CRUD/list/view on users without changing its password
        // authentication options or the ability to authenticate existing users.
        users.listRule = null
        users.viewRule = null
        users.createRule = null
        users.updateRule = null
        users.deleteRule = null
        app.save(users)
    },

    // -- DOWN --
    (app) => {
        throw new Error(
            "OpenTasks migration 011 cannot be rolled back: restore the pre-cutover PocketBase database and attachment-storage backups instead",
        )
    },
)
