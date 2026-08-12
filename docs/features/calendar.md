# Calendar

## Overview

The calendar feature shows dated tasks and countdowns in time-oriented views. It supports year, month, week, three-day, day, and list views. Users can switch view preferences, open tasks from calendar rows, and navigate to countdown detail when a countdown item is selected.

## User Flow

Calendar users can:

- Switch between year, month, week, three-day, day, and list views.
- Choose the list display style where supported.
- See tasks on their due dates, including all-day and multi-day tasks.
- See countdowns merged into calendar displays as all-day items.
- Open a task for editing from the calendar.
- Open countdown detail from countdown calendar entries.
- Import events from the platform calendar through Settings.

## Technical Design

`CalendarViewModel` combines task data from `ObserveTasksByDayUseCase`, countdown data from `ObserveAllCountdownsUseCase`, and persisted calendar preferences from settings use cases. Countdown items are mapped to task-like calendar rows with `Countdown.toCalendarTask()`.

Calendar UI lives under `ui/screens/calendar/`. The screen keeps view selection in app settings through `SaveCalendarViewPreferenceAction` and `SaveCalendarListDisplayModePreferenceAction`.

Calendar import is handled outside the calendar screen. Settings opens the import dialog, `FetchCalendarEventsUseCase` reads platform calendar data, and `ImportCalendarEventsAction` creates tasks in the stable `Calendar Imports` category with the `Imported` tag.

## Recurrence and projection boundaries

Countdown occurrences use the immutable civil-date anchor and anchor/index arithmetic. Only a bounded adjacent correction is allowed after the arithmetic estimate, so far-past/far-future dates do not require occurrence-by-occurrence iteration. `NONE` and non-positive intervals retain their fallback behavior, count-up dates continue to count from a future anchor, and monthly/yearly 29–31 or leap-day anchors clamp each occurrence to that occurrence's month without drifting the original anchor.

`CalendarTransforms` and `CalendarViewModel` own the calendar projection. On `Dispatchers.Default` they precompute row date/time text, all-day/timed fields, timeline labels, selected-day state, fixed preview prefixes, and overflow counts. The original task remains on each immutable row projection. Equality-based `scan` reuse preserves unchanged day/list identity, including day rollover updates. Calendar composables consume these projections; only a measured-height limit may select a prefix through the pure layout-boundary helper, with no formatting, sorting, grouping, or pixel constraint in the ViewModel.

## Shared Capabilities

- [Reminders](common/reminders.md) for task and countdown notifications visible from calendar workflows.
- [Import and Export](common/import-export.md) for platform calendar and ICS behavior.
- [Sync and Storage](common/sync-and-storage.md) for task/countdown persistence and sync.

## Current Limitations

The calendar is a task/countdown display and navigation surface. Calendar import creates tasks, but OpenTasks does not write changes back to the device calendar.
