# Countdowns

## Overview

Countdowns track days until or since important dates. They can represent holidays, birthdays, anniversaries, or general countdowns. Countdowns can appear on their own tab and in calendar views.

## User Flow

Users can:

- Create countdowns by type.
- Switch between counting down to a future date and counting up from a past date.
- Configure smart-list visibility so countdowns appear on the day, several days early, always, or remain hidden from smart lists.
- Add reminders.
- Configure recurrence for repeated events.
- Open countdown detail, edit the countdown, or delete it.

## Technical Design

Countdowns are stored in the `countdowns` Room table. `CountdownRepositoryImpl` owns persistence, timestamp conversion, soft deletion, and sync triggering.

`CountdownViewModel` observes and filters countdowns by type. `CountdownFormViewModel` handles create, edit, detail, and delete flows through `AddCountdownAction`, `UpdateCountdownAction`, `DeleteCountdownAction`, and `ObserveCountdownByIdUseCase`.

Calendar integration maps countdowns to task-like all-day rows with ids prefixed by `countdown_`. Type-to-priority color mapping is used only for display.

PocketBase sync uses `CountdownSyncAdapter` and `CountdownRecord`.

## Shared Capabilities

- [Reminders](common/reminders.md) for countdown reminder scheduling.
- [Sync and Storage](common/sync-and-storage.md) for Room, timestamps, soft deletes, and PocketBase sync.

## Current Limitations

Countdowns are displayed in calendar views, but they are not tasks. Task-only actions such as tagging, sections, task image attachments, and board status changes do not apply to countdowns.
