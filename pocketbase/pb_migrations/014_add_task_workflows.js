/// <reference path="../pb_data/types.d.ts" />

// Stop all clients for this coordinated schema upgrade. History is owner-scoped,
// independent of task/category lifetimes, and participates in account replacement.
migrate(
    (app) => {
        const users = app.findCollectionByNameOrId("users")
        const tasks = app.findCollectionByNameOrId("tasks")
        tasks.fields.add(new TextField({ name: "sourceNoteId" }))
        app.save(tasks)

        const authenticated = '@request.auth.id != ""'
        const owner = authenticated + ' && account = @request.auth.id'
        const history = new Collection({
            type: "base",
            name: "task_completions",
            listRule: owner,
            viewRule: owner,
            createRule: authenticated + ' && @request.body.account = @request.auth.id',
            updateRule: owner + ' && @request.body.account = account && @request.body.localUpdatedAt > localUpdatedAt',
            deleteRule: owner,
            fields: [
                { type: "relation", name: "account", collectionId: users.id, required: true, maxSelect: 1, cascadeDelete: false },
                { type: "text", name: "localId", required: true },
                { type: "text", name: "taskId", required: true },
                { type: "text", name: "title" },
                { type: "text", name: "categoryId" },
                { type: "text", name: "categoryName" },
                { type: "number", name: "completedAt", required: true },
                { type: "number", name: "occurrenceAt" },
                { type: "bool", name: "isDeleted" },
                { type: "number", name: "localCreatedAt", required: true },
                { type: "number", name: "localUpdatedAt", required: true },
            ],
            indexes: ["CREATE UNIQUE INDEX idx_task_completions_account_localId ON task_completions (account, localId)"],
        })
        app.save(history)
    },
    () => {
        throw new Error("Migration 014 retains completion history. Restore coordinated client/server backups to roll back.")
    },
)
