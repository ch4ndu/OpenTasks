# Reminders

## Overview

Reminders are shared scheduling behavior used by tasks and countdowns. The app stores reminder configuration in the feature entity and schedules platform notifications through domain actions.

## Task Reminders

Tasks support deadline-based reminders. The task model includes legacy/simple reminder fields (`notifyBeforeValue`, `notifyBeforeUnit`) and richer reminder strings for duration-based and date-based reminder options.

Task reminder scheduling is handled by `ScheduleTaskRemindersAction`. Broad rescheduling is handled by `RescheduleAllRemindersAction`, including app startup, sync/background flows, and platform events that require rebuilding alarms.

All-day task notifications can use ongoing notification behavior on Android so users can mark the task done or dismiss the notification.

## Countdown Reminders

Countdowns store reminder choices in the `reminders` field. `ScheduleCountdownRemindersAction` schedules countdown notifications, and `RescheduleAllCountdownRemindersAction` rebuilds them when needed.

Countdown reminders are independent from task reminders. Countdowns can also recur, which affects future scheduling.

## Platform Behavior

`NotificationScheduler` is an expect/actual platform abstraction.

- Android schedules alarms and posts notifications through Android notification APIs.
- iOS uses platform notification APIs.
- JVM currently provides no-op notification behavior.

Settings exposes notification permission status and exact reminder timing status. On Android versions that require exact alarm permission, Settings can route users to the platform exact reminder settings screen.

## Related Docs

- [Tasks](../tasks.md)
- [Countdowns](../countdowns.md)
- [Settings](../settings.md)
- [Sync and Storage](sync-and-storage.md)

## Current Limitations

Reminder delivery depends on platform permission state and OS scheduling behavior. JVM desktop does not currently deliver notifications.
