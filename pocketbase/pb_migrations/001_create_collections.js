/// <reference path="../pb_data/types.d.ts" />

/**
 * OpenTasks PocketBase migration — creates the tasks, categories, and notes collections.
 *
 * Usage:
 *   1. Copy the `pocketbase/` folder to your server (or keep it next to the binary).
 *   2. Run:  ./pocketbase serve
 *   PocketBase auto-runs migrations in pb_migrations/ on first start.
 */

migrate(
    // ── UP ───────────────────────────────────────────────────────────────────
    (app) => {
        // ── Categories ──────────────────────────────────────────────────────
        const categories = new Collection({
            name: "categories",
            type: "base",
            listRule: "",
            viewRule: "",
            createRule: "",
            updateRule: "",
            deleteRule: "",
            fields: [
                { name: "localId",        type: "text", required: true },
                { name: "name",           type: "text", required: true },
                { name: "icon",           type: "text" },
                { name: "sortOrder",      type: "number" },
                { name: "isDeleted",      type: "bool" },
                { name: "localCreatedAt", type: "number" },
                { name: "localUpdatedAt", type: "number" },
            ],
        })
        // Index on localId for fast lookups during sync
        categories.indexes = [
            "CREATE UNIQUE INDEX idx_categories_localId ON categories (localId)",
        ]
        app.save(categories)

        // ── Tasks ───────────────────────────────────────────────────────────
        const tasks = new Collection({
            name: "tasks",
            type: "base",
            listRule: "",
            viewRule: "",
            createRule: "",
            updateRule: "",
            deleteRule: "",
            fields: [
                { name: "localId",            type: "text",   required: true },
                { name: "title",              type: "text",   required: true },
                { name: "content",            type: "text" },
                { name: "priority",           type: "text" },
                { name: "deadline",           type: "number" },
                { name: "endDeadline",        type: "number" },
                { name: "notifyBeforeValue",  type: "number" },
                { name: "notifyBeforeUnit",   type: "text" },
                { name: "recurrenceType",     type: "text" },
                { name: "recurrenceInterval", type: "number" },
                { name: "isCompleted",        type: "bool" },
                { name: "isUrgent",           type: "bool" },
                { name: "isImportant",        type: "bool" },
                { name: "categoryId",         type: "text" },
                { name: "isAllDay",           type: "bool" },
                { name: "sourceExternalId",   type: "text" },
                { name: "location",           type: "text" },
                { name: "url",                type: "text" },
                { name: "organizer",          type: "text" },
                { name: "eventStatus",        type: "text" },
                { name: "attendees",          type: "text" },
                { name: "durationReminders",  type: "text" },
                { name: "dateReminders",      type: "text" },
                { name: "isDeleted",          type: "bool" },
                { name: "localCreatedAt",     type: "number" },
                { name: "localUpdatedAt",     type: "number" },
            ],
        })
        tasks.indexes = [
            "CREATE UNIQUE INDEX idx_tasks_localId ON tasks (localId)",
        ]
        app.save(tasks)

        // ── Notes ───────────────────────────────────────────────────────────
        const notes = new Collection({
            name: "notes",
            type: "base",
            listRule: "",
            viewRule: "",
            createRule: "",
            updateRule: "",
            deleteRule: "",
            fields: [
                { name: "localId",        type: "text", required: true },
                { name: "title",          type: "text" },
                { name: "content",        type: "text" },
                { name: "isDeleted",      type: "bool" },
                { name: "localCreatedAt", type: "number" },
                { name: "localUpdatedAt", type: "number" },
            ],
        })
        notes.indexes = [
            "CREATE UNIQUE INDEX idx_notes_localId ON notes (localId)",
        ]
        app.save(notes)
    },

    // ── DOWN ─────────────────────────────────────────────────────────────────
    (app) => {
        app.delete(app.findCollectionByNameOrId("notes"))
        app.delete(app.findCollectionByNameOrId("tasks"))
        app.delete(app.findCollectionByNameOrId("categories"))
    },
)
