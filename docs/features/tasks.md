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

Users create tasks from the matrix, task list, calendar, system share intake, and import flows. On Matrix, Task List, Calendar, and Quadrant Detail, the floating action button opens a chooser between Quick Add and the full task editor. The create/edit task screen owns detailed form state until save, while Quick Add owns only its entry-scoped title and inference state; both write through domain actions.

Important task workflows:

- Create or edit a title, rich description, due date, recurrence, priority, category, and section.
- Add subtasks as structured checklist rows stored separately from the rich description.
- Mark tasks as To Do, In Progress, or Done.
- Star tasks and use the Starred smart filter.
- Assign tags for cross-category organization.
- Open the Tasks tab on an overview of the six smart views (Today, Overdue, Starred, No date, High priority, Due this week) and the user's lists, each with a live count of unfinished tasks. Tapping one opens that view inside Tasks; All views or Back returns to the overview, and returning from a task editor restores the open view.
- Create a list from the overview's New list action.
- Sort task lists by Recently Updated, Deadline, Priority, or Title.
- Add more details such as section, location, URL, organizer, event status, and attendees.
- Open a task location in the platform maps app when a location is present.
- Add task images from gallery or camera where the platform supports it.
- Open task reminder notifications into a home-screen bottom sheet with Mark Done, Got It, and Edit Task actions.
- Delete tasks through soft deletion so sync can propagate tombstones.

## Shortlist

Shortlist is an explicit, persistent selection of unfinished tasks to tackle next. The overview shows it as a card above the smart views with its remaining count.

- Choose tasks opens a sheet with Suggestions and All tasks. Suggestions group unfinished, non-shortlisted tasks by the first matching rule: Not Urgent & Important (MEDIUM priority), then due today, then no due date. All tasks lists every unfinished, non-shortlisted task. Tasks are added only through their Add control.
- Completing a task removes it from the Shortlist, including when a recurring occurrence advances. Reopening does not restore membership. Remove from Shortlist in the row menu clears membership only; it never deletes or edits the task.
- The task editor has an Add to / Remove from Shortlist action for unfinished tasks. It commits immediately, like Delete, and is not part of the draft.
- Due & overdue shows unfinished overdue or due-today tasks outside the Shortlist and links to Today.
- Membership never resets by date and never changes due dates, reminders, list, star, or priority. Rows follow the task-list sort option with task ID as the tie-breaker.

## Quick Add

Quick Add is an offline, deterministic English parser for short task titles. It recognizes only trailing, token-bounded clauses:

- Dates: `today`, `tomorrow`, `in N days` for 1–365, `in N weeks` for 1–52, and full weekday names.
- Times: `at H am`, `at H pm`, `at H:MM am`, `at H:MM pm`, and unambiguous 24-hour `at HH:MM`.
- Recurrence: `daily`/`every day`, `weekly`/`every week`, `monthly`/`every month`, `yearly`/`every year`, `every weekday`, and `every <weekday>`.

Recognized clauses appear as removable chips. Removing a chip keeps that phrase as literal title text; editing the phrase makes it eligible for recognition again. Unsupported or invalid phrases remain literal. Quick Add does not infer categories, tags, priorities, reminders, arbitrary recurrence intervals, or fuzzy/AI intent.

The opening surface supplies category, priority, and an optional Calendar civil date. An explicit parsed date overrides only the fallback date, a parsed time overrides only the time/all-day inference, and parsed recurrence overrides only recurrence. Date-only tasks use the existing 08:00 local storage anchor and create no reminder. A time without any explicit or contextual date uses today when still future and tomorrow otherwise. All other task fields use `AddTaskAction` defaults.

## Technical Design

Task data is stored in the `tasks` Room table. `TaskRepositoryImpl` wraps `TaskDao`, converts timestamps between local app time and UTC storage, and triggers sync after writes.

Reads flow through task use cases such as `ObserveTasksForFilterUseCase`, `ObserveTaskOverviewCountsUseCase`, `ObserveTasksByPriorityUseCase`, and `ObserveTaskByIdUseCase`. Smart-view membership is one shared predicate (`TaskListFilter.includes`) used by both the destination list and the overview counts; counts include only unfinished (TODO or IN_PROGRESS) tasks, while Starred, category, and Today destinations still show their completed section. Writes flow through task actions such as `AddTaskAction`, `UpdateTaskAction`, `DeleteTaskAction`, `ToggleTaskCompleteAction`, `UpdateTaskStatusAction`, `UpdateSectionAction`, and import/export actions.

Screen state lives in feature ViewModels:

- `MatrixViewModel` projects tasks for matrix list and board modes.
- `TaskOverviewViewModel` projects smart-view and list counts for the Tasks overview and creates lists.
- `TaskListViewModel` is bound to one `Screen.TaskListDestination` filter and projects that view's tasks, sort options, and board/list modes.
- `TaskFormViewModel` owns create/edit save flow, including the duplicate-save guard and pending task image handoff.
- `QuickAddTaskViewModel` captures one reference time and active-cache boundary for its Navigation entry, owns parsing and chip dismissal, rejects duplicate or stale-boundary saves, and delegates persistence to `AddTaskAction`.

The Tasks-tab Add task action creates in the open list's category; the overview and smart views create in Inbox without inferring a date, priority, or star. The task widget's list action has no category and opens the overview.

The full editor retains the complete entry draft across recreation. Editing only text preserves the task's exact valid start/end values, including seconds and milliseconds, overnight ranges, multi-day spans, and imported all-day exclusive end dates. Moving the start date shifts the end date by the same civil-day distance while retaining its wall-clock time; explicit time changes keep the established civil-day span, while a newly created duration starts as same-day. Clearing the date clears the end date, and Save shows a localized validation error without persisting when the end precedes the start. Retained multi-day ranges remain visible in the date summary and duration tab.

Ordinary Save preserves TODO and IN_PROGRESS. Marking the task complete uses DONE and keeps the existing recurring-completion prompt. Turning completion off restores the entry's original non-DONE status, or TODO when the editor opened a DONE task.

Task detail fields live in the create/edit task flow. Location, URL, organizer, event status, and attendees are stored on the task so imported calendar metadata and manually entered details survive local persistence and sync.

Task reminder notification taps route through a shared notification event payload. Task notifications open the matrix home tab and show a task notification bottom sheet. Mark Done follows `MarkTaskNotificationDoneAction` through `UpdateTaskAction` and `TaskWriteIntent.NotificationMarkDone`, so the persisted-truth coordinator advances the notified recurring occurrence. Got It mirrors Android's notification action semantics by dismissing the all-day ongoing notification state without completing the task or cancelling future reminders. Countdown notification taps continue to open countdown detail.

Shortlist membership is the synced `tasks.isShortlisted` field. `SetTaskShortlistedAction` writes it through `TaskWriteIntent.SetShortlisted`; `TaskWriteNormalizer` clears it whenever a task is persisted as DONE and `TaskWriteCoordinator` clears it when a recurring occurrence advances. `ObserveShortlistUseCase` projects the Shortlist, suggestions, and the Due & overdue count for `ShortlistViewModel`.

Task sync uses `TaskSyncAdapter` and `TaskRecord`. Task-tag assignments are separate `task_tags` records. Task images are separate `attachments` records with `ownerType = "task"`.

## Shared Capabilities

- [Attachments](common/attachments.md) for task image storage and sync.
- [Reminders](common/reminders.md) for task reminder scheduling and notification permissions.
- [Sync and Storage](common/sync-and-storage.md) for Room, timestamps, soft deletes, and PocketBase sync.
- [Import and Export](common/import-export.md) for calendar, ICS, CSV, and share-to-task intake.

## Current Limitations

Attachments are currently wired only for task images. The attachment infrastructure is owner-based and intended to support other owners later, but notes and countdowns do not have attachment UI or domain actions yet.
