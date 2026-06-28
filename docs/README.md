# OpenTasks Documentation

This folder contains durable project documentation for app functionality and implementation architecture.

## Feature Docs

- [Tasks](features/tasks.md) - task creation, organization, matrix/list/board views, subtasks, recurrence, and task images.
- [Calendar](features/calendar.md) - calendar views, task/countdown display, and calendar interactions.
- [Notes](features/notes.md) - rich note creation, editing, storage, and sync.
- [Countdowns](features/countdowns.md) - countdown types, visibility, recurrence, and reminders.
- [Settings](features/settings.md) - preferences, sync setup, import/export entry points, and local data reset.
- [Widgets](features/widgets.md) - Android task, calendar, and week widgets.

## Shared Capability Docs

- [Attachments](features/common/attachments.md) - owner-based attachment design, current task image behavior, local storage, and PocketBase file sync.
- [Reminders](features/common/reminders.md) - task and countdown notification behavior.
- [Sync and Storage](features/common/sync-and-storage.md) - Room, timestamps, soft deletes, PocketBase sync, and migrations.
- [Import and Export](features/common/import-export.md) - calendar import, ICS, CSV, and system share intake.

## Project Docs

- [README](../README.md) - project overview, screenshots, build steps, and PocketBase setup.
- [Roadmap](../ROADMAP.md) - planned and completed feature ideas.

## Development Guidance

The `docs/ai/` files are implementation and review rules for AI-assisted development. They are not feature manuals, but they remain the canonical source for architecture conventions:

- [Architecture](ai/architecture.md)
- [Feature Implementation](ai/feature-implementation.md)
- [Data, Sync, and Date/Time](ai/data-sync-time.md)
- [UI and Compose](ai/ui.md)
- [Android Widgets](ai/widgets.md)
- [Audit Checklist](ai/audit.md)
