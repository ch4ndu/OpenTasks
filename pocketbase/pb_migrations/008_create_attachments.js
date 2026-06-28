/// <reference path="../pb_data/types.d.ts" />

/**
 * OpenTasks PocketBase migration — creates the attachments collection.
 */

migrate(
    // -- UP --
    (app) => {
        const attachments = new Collection({
            name: "attachments",
            type: "base",
            listRule: "",
            viewRule: "",
            createRule: "",
            updateRule: "",
            deleteRule: "",
            fields: [
                { name: "localId",        type: "text", required: true },
                { name: "ownerType",      type: "text", required: true },
                { name: "ownerId",        type: "text", required: true },
                { name: "kind",           type: "text", required: true },
                {
                    name: "file",
                    type: "file",
                    maxSelect: 1,
                    maxSize: 5242880,
                    mimeTypes: ["image/jpeg", "image/png", "image/webp"],
                },
                { name: "mimeType",       type: "text" },
                { name: "fileName",       type: "text" },
                { name: "fileSizeBytes",  type: "number" },
                { name: "width",          type: "number" },
                { name: "height",         type: "number" },
                { name: "sortOrder",      type: "number" },
                { name: "isDeleted",      type: "bool" },
                { name: "localCreatedAt", type: "number" },
                { name: "localUpdatedAt", type: "number" },
            ],
        })
        attachments.indexes = [
            "CREATE UNIQUE INDEX idx_attachments_localId ON attachments (localId)",
            "CREATE INDEX idx_attachments_owner ON attachments (ownerType, ownerId, kind, isDeleted, sortOrder)",
            "CREATE INDEX idx_attachments_localUpdatedAt ON attachments (localUpdatedAt)",
        ]
        app.save(attachments)
    },

    // -- DOWN --
    (app) => {
        app.delete(app.findCollectionByNameOrId("attachments"))
    },
)
