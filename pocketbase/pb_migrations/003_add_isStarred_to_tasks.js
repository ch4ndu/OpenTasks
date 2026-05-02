/// <reference path="../pb_data/types.d.ts" />

/**
 * OpenTasks PocketBase migration — adds isStarred field to tasks collection.
 */

migrate(
    // -- UP --
    (app) => {
        const tasks = app.findCollectionByNameOrId("tasks")
        tasks.fields.add(new BoolField({ name: "isStarred" }))
        app.save(tasks)
    },

    // -- DOWN --
    (app) => {
        const tasks = app.findCollectionByNameOrId("tasks")
        const field = tasks.fields.getByName("isStarred")
        if (field) {
            tasks.fields.removeById(field.id)
        }
        app.save(tasks)
    },
)
