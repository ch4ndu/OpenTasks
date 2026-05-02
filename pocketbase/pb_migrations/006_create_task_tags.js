/// <reference path="../pb_data/types.d.ts" />

/**
 * OpenTasks PocketBase migration — creates the task_tags collection.
 */

migrate(
    // -- UP --
    (app) => {
        const taskTags = new Collection({
            name: "task_tags",
            type: "base",
            listRule: "",
            viewRule: "",
            createRule: "",
            updateRule: "",
            deleteRule: "",
            fields: [
                { name: "localId",        type: "text", required: true },
                { name: "taskId",         type: "text", required: true },
                { name: "tagId",          type: "text", required: true },
                { name: "isDeleted",      type: "bool" },
                { name: "localCreatedAt", type: "number" },
                { name: "localUpdatedAt", type: "number" },
            ],
        })
        taskTags.indexes = [
            "CREATE UNIQUE INDEX idx_task_tags_localId ON task_tags (localId)",
            "CREATE INDEX idx_task_tags_taskId ON task_tags (taskId)",
            "CREATE INDEX idx_task_tags_tagId ON task_tags (tagId)",
            "CREATE INDEX idx_task_tags_localUpdatedAt ON task_tags (localUpdatedAt)",
        ]
        app.save(taskTags)
    },

    // -- DOWN --
    (app) => {
        app.delete(app.findCollectionByNameOrId("task_tags"))
    },
)
