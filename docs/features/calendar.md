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

Reimagined adds an anchored date picker with year selection, Today, and strict `YYYY-MM-DD` entry. Invalid dates and dates outside the current view's pager range show an error rather than jumping to a clamped date. Month keeps its grid and selected-date agenda visible, and Year adapts its columns to window width. Week retains Classic's swipeable two-column, four-row layout: a mini month calendar in the first cell and seven day cells. Day cells select a day and task bars open task details. Reimagined Week’s +N overflow opens the full-day dialog; Month cell selection exposes the full agenda in both compact and wide layouts. Three-day retains the shared timeline at every window width, with hour labels on the left and three day columns advancing one day per horizontal swipe. Day retains its hour gutter even on empty days. Day and Three-day assign overlapping timed items separate lanes with a full-item list for dense schedules. The Reimagined topbar contains the date dropdown, Today icon, Add, and Settings as its rightmost action; all six modes remain labeled controls below it. List exposes Timeline and Cards controls.

Reimagined also displays task continuations on each intersecting civil date in the visible calendar window. Midnight ending dates are excluded, including imported all-day exclusive ends. Continuation rows keep the original task identity and clip their displayed times to that day's segment. Classic retains its existing start-date placement. Projection expands only the bounded visible window and neighboring pager pages, so a task spanning many years cannot cause an unbounded expansion.

Reimagined view-save failures preserve the current selection and show a dismissible error. Date jumps within the same view do not wait on preference persistence; view writes remain owned by the ViewModel when navigation is cancelled. Out-of-range navigation errors are also dismissible.

Read failures offer a retry of the local data stream. Authenticated sync failures retain cached items and expose the existing refresh action; a sync failure is not labeled as proof that the device is offline. Local-only mode does not show network status.

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
