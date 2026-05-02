/// <reference path="../pb_data/types.d.ts" />

/**
 * OpenTasks PocketBase migration — adds subtasks JSON field to tasks collection.
 */

migrate(
    (app) => {
        const tasks = app.findCollectionByNameOrId("tasks")
        tasks.fields.add(new JSONField({ name: "subtasks" }))
        app.save(tasks)
    },
    (app) => {
        const tasks = app.findCollectionByNameOrId("tasks")
        const field = tasks.fields.getByName("subtasks")
        if (field) {
            tasks.fields.removeById(field.id)
        }
        app.save(tasks)
    },
)
