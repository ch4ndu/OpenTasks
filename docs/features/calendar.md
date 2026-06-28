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

## Shared Capabilities

- [Reminders](common/reminders.md) for task and countdown notifications visible from calendar workflows.
- [Import and Export](common/import-export.md) for platform calendar and ICS behavior.
- [Sync and Storage](common/sync-and-storage.md) for task/countdown persistence and sync.

## Current Limitations

The calendar is a task/countdown display and navigation surface. Calendar import creates tasks, but OpenTasks does not write changes back to the device calendar.
