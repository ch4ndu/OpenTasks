# Android Widgets

## Overview

OpenTasks includes Android Glance widgets for quick task and calendar access from the home screen. Widget functionality is Android-only.

## User Flow

Users can add and configure:

- Task widget for filtered task lists.
- Calendar widget for month-style task and countdown indicators.
- Week widget for week-style task and countdown indicators.

Task widget filters include all tasks, today, tomorrow, next seven days, and category. Sorting supports date, priority, and name where the widget configuration exposes it.

## Technical Design

Widget code lives in `androidApp/src/main/kotlin/.../widget/`. `WidgetDataProvider` reads DAOs directly as a boundary exception because widgets need a compact Android-only data source outside normal screen ViewModels.

Widgets use Glance state for preferences and refresh triggers. `WidgetRefreshCallbacks` integrates app writes and sync events with widget refresh behavior. Widget click actions launch app activities or navigation targets through Android intents.

Calendar and week widgets merge task dates and countdown dates into compact day summaries. To keep widgets readable, they cap the number of displayed items per day.

## Shared Capabilities

- [Sync and Storage](common/sync-and-storage.md) for local data reads and refresh behavior after sync.
- [Reminders](common/reminders.md) for the notification flows that may bring users back to task screens.

## Current Limitations

Widgets are available only on Android. They intentionally use compact summaries and do not expose the full task editor, notes, or attachment viewer inline.
