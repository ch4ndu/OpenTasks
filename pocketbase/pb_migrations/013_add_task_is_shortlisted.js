/// <reference path="../pb_data/types.d.ts" />

/** Adds Shortlist membership to tasks; existing records read back as false. */
migrate(
    (app) => {
        const tasks = app.findCollectionByNameOrId("tasks")
        tasks.fields.add(new BoolField({ name: "isShortlisted" }))
        app.save(tasks)
    },
    (app) => {
        const tasks = app.findCollectionByNameOrId("tasks")
        tasks.fields.removeByName("isShortlisted")
        app.save(tasks)
    },
)
