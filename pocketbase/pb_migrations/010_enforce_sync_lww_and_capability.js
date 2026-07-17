/// <reference path="../pb_data/types.d.ts" />

/**
 * Protects OpenTasks' app-managed LWW timestamp at the server boundary and
 * exposes a read-only capability record used before a client adopts a server.
 */
migrate(
    (app) => {
        const guardedCollections = [
            "categories",
            "tags",
            "tasks",
            "attachments",
            "task_tags",
            "notes",
            "countdowns",
        ]
        for (const name of guardedCollections) {
            const collection = app.findCollectionByNameOrId(name)
            collection.updateRule = "@request.body.localUpdatedAt > localUpdatedAt"
            app.save(collection)
        }

        const meta = new Collection({
            name: "opentasks_sync_meta",
            type: "base",
            listRule: "",
            viewRule: "",
            createRule: null,
            updateRule: null,
            deleteRule: null,
            fields: [
                { name: "capabilityVersion", type: "number", required: true },
                { name: "serverInstanceId", type: "text", required: true },
            ],
        })
        app.save(meta)

        const record = new Record(meta)
        record.set("capabilityVersion", 1)
        record.set("serverInstanceId", $security.randomString(32))
        app.save(record)
    },
    (app) => {
        const guardedCollections = [
            "categories",
            "tags",
            "tasks",
            "attachments",
            "task_tags",
            "notes",
            "countdowns",
        ]
        for (const name of guardedCollections) {
            const collection = app.findCollectionByNameOrId(name)
            collection.updateRule = ""
            app.save(collection)
        }
        app.delete(app.findCollectionByNameOrId("opentasks_sync_meta"))
    },
)
