/// <reference path="../pb_data/types.d.ts" />

/**
 * OpenTasks PocketBase migration — creates the tags and countdowns collections.
 */

migrate(
    // -- UP --
    (app) => {
        // -- Tags --
        const tags = new Collection({
            name: "tags",
            type: "base",
            listRule: "",
            viewRule: "",
            createRule: "",
            updateRule: "",
            deleteRule: "",
            fields: [
                { name: "localId",        type: "text", required: true },
                { name: "name",           type: "text", required: true },
                { name: "color",          type: "text" },
                { name: "isDeleted",      type: "bool" },
                { name: "localCreatedAt", type: "number" },
                { name: "localUpdatedAt", type: "number" },
            ],
        })
        tags.indexes = [
            "CREATE UNIQUE INDEX idx_tags_localId ON tags (localId)",
        ]
        app.save(tags)

        // -- Countdowns --
        const countdowns = new Collection({
            name: "countdowns",
            type: "base",
            listRule: "",
            viewRule: "",
            createRule: "",
            updateRule: "",
            deleteRule: "",
            fields: [
                { name: "localId",              type: "text",   required: true },
                { name: "title",                type: "text",   required: true },
                { name: "targetDate",           type: "number" },
                { name: "countdownType",        type: "text" },
                { name: "countingMode",         type: "text" },
                { name: "reminders",            type: "text" },
                { name: "recurrenceType",       type: "text" },
                { name: "recurrenceInterval",   type: "number" },
                { name: "recurrenceDaysOfWeek", type: "text" },
                { name: "smartListVisibility",  type: "text" },
                { name: "isCompleted",          type: "bool" },
                { name: "isDeleted",            type: "bool" },
                { name: "localCreatedAt",       type: "number" },
                { name: "localUpdatedAt",       type: "number" },
            ],
        })
        countdowns.indexes = [
            "CREATE UNIQUE INDEX idx_countdowns_localId ON countdowns (localId)",
        ]
        app.save(countdowns)
    },

    // -- DOWN --
    (app) => {
        app.delete(app.findCollectionByNameOrId("countdowns"))
        app.delete(app.findCollectionByNameOrId("tags"))
    },
)
