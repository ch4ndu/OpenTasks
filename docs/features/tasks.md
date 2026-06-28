# Tasks

## Overview

Tasks are the main work item in OpenTasks. A task can have a title, rich-text description, subtasks, category, section, tags, priority, status, due date range, recurrence, reminders, calendar metadata, and image attachments.

Tasks appear in multiple views:

- Eisenhower Matrix groups tasks by priority quadrant.
- Task List shows category and smart-filter views.
- Board mode groups tasks by status.
- Calendar shows dated tasks alongside countdowns.
- Android widgets show filtered task summaries.

Task image attachment behavior is documented in [Attachments](common/attachments.md).

## User Flow

Users create tasks from the matrix, task list, calendar, system share intake, and import flows. The create/edit task screen owns form state until save, then the ViewModel writes through domain actions.

Important task workflows:

- Create or edit a title, rich description, due date, recurrence, priority, category, and section.
- Add subtasks as structured checklist rows stored separately from the rich description.
- Mark tasks as To Do, In Progress, or Done.
- Star tasks and use the Starred smart filter.
- Assign tags for cross-category organization.
- Use task list filters for category, Starred, Today, Overdue, No Date, High Priority, and Due This Week views.
- Sort task lists by Recently Updated, Deadline, Priority, or Title.
- Add more details such as section, location, URL, organizer, event status, and attendees.
- Open a task location in the platform maps app when a location is present.
- Add task images from gallery or camera where the platform supports it.
- Delete tasks through soft deletion so sync can propagate tombstones.

## Technical Design

Task data is stored in the `tasks` Room table. `TaskRepositoryImpl` wraps `TaskDao`, converts timestamps between local app time and UTC storage, and triggers sync after writes.

Reads flow through task use cases such as `ObserveAllTasksUseCase`, `ObserveTasksByPriorityUseCase`, `ObserveTasksForCategoryUseCase`, `ObserveTodayTasksUseCase`, and `ObserveTaskByIdUseCase`. Writes flow through task actions such as `AddTaskAction`, `UpdateTaskAction`, `DeleteTaskAction`, `ToggleTaskCompleteAction`, `UpdateTaskStatusAction`, `UpdateSectionAction`, and import/export actions.

Screen state lives in feature ViewModels:

- `MatrixViewModel` projects tasks for matrix list and board modes.
- `TaskListViewModel` projects tasks for category filters, smart filters, sort options, and board/list modes.
- `TaskFormViewModel` owns create/edit save flow, including the duplicate-save guard and pending task image handoff.

Task detail fields live in the create/edit task flow. Location, URL, organizer, event status, and attendees are stored on the task so imported calendar metadata and manually entered details survive local persistence and sync.

Task sync uses `TaskSyncAdapter` and `TaskRecord`. Task-tag assignments are separate `task_tags` records. Task images are separate `attachments` records with `ownerType = "task"`.

## Shared Capabilities

- [Attachments](common/attachments.md) for task image storage and sync.
- [Reminders](common/reminders.md) for task reminder scheduling and notification permissions.
- [Sync and Storage](common/sync-and-storage.md) for Room, timestamps, soft deletes, and PocketBase sync.
- [Import and Export](common/import-export.md) for calendar, ICS, CSV, and share-to-task intake.

## Current Limitations

Attachments are currently wired only for task images. The attachment infrastructure is owner-based and intended to support other owners later, but notes and countdowns do not have attachment UI or domain actions yet.
