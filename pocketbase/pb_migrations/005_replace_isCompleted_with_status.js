/// <reference path="../pb_data/types.d.ts" />

migrate(
    (app) => {
        // 1. Add the status field
        const tasks = app.findCollectionByNameOrId("tasks")
        tasks.fields.add(new TextField({ name: "status" }))
        app.save(tasks)

        // 2. Migrate existing data: isCompleted=true → status="DONE", else "TODO"
        app.db().newQuery(
            "UPDATE tasks SET status = CASE WHEN isCompleted = TRUE THEN 'DONE' ELSE 'TODO' END"
        ).execute()

        // 3. Remove the old isCompleted field
        const oldField = tasks.fields.getByName("isCompleted")
        if (oldField) {
            tasks.fields.removeById(oldField.id)
            app.save(tasks)
        }
    },
    (app) => {
        const tasks = app.findCollectionByNameOrId("tasks")

        // Restore isCompleted field
        tasks.fields.add(new BoolField({ name: "isCompleted" }))
        app.save(tasks)

        // Migrate data back: status="DONE" → isCompleted=true
        app.db().newQuery(
            "UPDATE tasks SET isCompleted = CASE WHEN status = 'DONE' THEN TRUE ELSE FALSE END"
        ).execute()

        // Remove status field
        const field = tasks.fields.getByName("status")
        if (field) { tasks.fields.removeById(field.id) }
        app.save(tasks)
    },
)
