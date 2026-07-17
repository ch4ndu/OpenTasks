/// <reference path="../pb_data/types.d.ts" />

/** Stores task recurrence anchors and completion instants without changing existing records. */
migrate(
    (app) => {
        const tasks = app.findCollectionByNameOrId("tasks")
        tasks.fields.add({ name: "recurrenceAnchorDay", type: "number" })
        tasks.fields.add({ name: "completedAt", type: "number" })
        app.save(tasks)
    },
    (app) => {
        const tasks = app.findCollectionByNameOrId("tasks")
        tasks.fields.removeByName("recurrenceAnchorDay")
        tasks.fields.removeByName("completedAt")
        app.save(tasks)
    },
)
