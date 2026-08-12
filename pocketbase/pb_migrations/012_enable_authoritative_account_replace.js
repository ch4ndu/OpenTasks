/// <reference path="../pb_data/types.d.ts" />

/**
 * Enables the explicitly confirmed OpenTasks account-replacement protocol.
 * Normal application deletes remain tombstones; hard delete is available only
 * to the authenticated owner and is used by the replacement executor.
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

const CAPABILITY_VERSION = 2
const AUTHORITATIVE_REPLACE_VERSION = 1
const OWNER_DELETE_RULE = '@request.auth.id != "" && account = @request.auth.id'

function fail(message) {
    throw new Error(`OpenTasks migration 012 aborted: ${message}`)
}

function requireCollection(app, name) {
    try {
        return app.findCollectionByNameOrId(name)
    } catch (error) {
        fail(`required collection ${name} is missing`)
    }
}

migrate(
    // -- UP --
    (app) => {
        for (const name of SYNC_COLLECTIONS) {
            const collection = requireCollection(app, name)
            const account = collection.fields.getByName("account")
            if (!account || account.type() !== "relation" || !account.required || account.maxSelect > 1) {
                fail(`${name}.account is not the required single-owner relation from migration 011`)
            }
            collection.deleteRule = OWNER_DELETE_RULE
            app.save(collection)
        }

        const meta = requireCollection(app, "opentasks_sync_meta")
        const records = app.findAllRecords(meta)
        if (records.length !== 1 || records[0].getInt("capabilityVersion") !== CAPABILITY_VERSION) {
            fail("opentasks_sync_meta must contain exactly one capabilityVersion 2 record")
        }

        const existing = meta.fields.getByName("authoritativeReplaceVersion")
        if (existing && existing.type() !== "number") {
            fail("opentasks_sync_meta.authoritativeReplaceVersion has an incompatible schema")
        }
        if (!existing) {
            meta.fields.add(new NumberField({
                name: "authoritativeReplaceVersion",
                required: false,
                min: 0,
            }))
            app.save(meta)
        }

        records[0].set("authoritativeReplaceVersion", AUTHORITATIVE_REPLACE_VERSION)
        app.save(records[0])

        const field = meta.fields.getByName("authoritativeReplaceVersion")
        field.required = true
        app.save(meta)

        const verified = app.findAllRecords(meta)
        if (verified.length !== 1 ||
            verified[0].getInt("capabilityVersion") !== CAPABILITY_VERSION ||
            verified[0].getInt("authoritativeReplaceVersion") !== AUTHORITATIVE_REPLACE_VERSION) {
            fail("authoritative replacement capability verification failed")
        }
    },

    // -- DOWN --
    (app) => {
        throw new Error(
            "OpenTasks migration 012 cannot be rolled back: restore the pre-migration PocketBase database and attachment-storage backups instead",
        )
    },
)
