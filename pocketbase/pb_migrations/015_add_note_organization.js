/// <reference path="../pb_data/types.d.ts" />

// Coordinated upgrade: stop old clients before applying, then update all clients.
migrate(
    (app) => {
        const notes = app.findCollectionByNameOrId("notes")
        notes.fields.add(new BoolField({ name: "isPinned" }))
        notes.fields.add(new BoolField({ name: "isArchived" }))
        notes.fields.add(new TextField({ name: "tagIds", max: 100000 }))
        notes.fields.add(new TextField({ name: "linkedNoteIds", max: 100000 }))
        app.save(notes)
    },
    () => {
        throw new Error("Migration 015 retains note organization. Restore coordinated client/server backups to roll back.")
    },
)
