# Android Glance Widget Rules

Load this for Android widget changes under `androidMain`.

## Location

- Widget code lives under `androidApp/src/main/kotlin/com/udnahc/opentasks/widget/`.
- Widget colors live in `androidApp/src/main/res/values/colors.xml`; SYSTEM colors require matching `values-night/` resources.

## Glance Rules

- Widget colors must use resource IDs: `ColorProvider(R.color.xxx)`.
- Do not use raw color ints like `ColorProvider(0xFFxxxxxx.toInt())`; they can silently crash widgets.
- Use `actionStartActivity(Intent)` with `ComponentName` for click actions.
- Do not rely on `LocalContext.current` for widget click launch behavior.
- Widget taps publish a `WidgetNavigationEvent` through `MainActivity` on both creation and every new intent. Its monotonic ID makes repeated identical actions observable; `view_calendar` carries its exact civil date, while `view_task` carries its task ID.
- Calendar and week widget ranges include DONE tasks through the dedicated DAO range query. Task-list widgets remain DONE-exclusive. Countdown rows must be projected to their effective recurring occurrence before day bucketing.
- Widget settings do not support opacity or grouping. Do not restore their preference fields, storage keys, controls, or preview behavior.

## Data Refresh

- Fetch data inside `provideContent` with `produceState` keyed on Glance state.
- Do not capture stale closures from `provideGlance`; it runs once per session.
- Use `updateAppWidgetState` to bump a refresh trigger, then call `update()` to trigger a fresh fetch.
- Use `TaskWidget.refreshWidget()` or `refreshAllWidgets()`.
- Do not call `instance.update()` directly outside widget-owned refresh helpers. The helpers may call `instance.update()` after `updateAppWidgetState`.
- Widget reads/actions, notification-driven refreshes, and scheduled maintenance use the validated active-cache owner/epoch, so they work in both local-only and PocketBase modes and reject stale callbacks before DAO access.
- `SyncWorker` captures one active boundary, skips the network pass in local-only mode, and still attempts reminder plus widget maintenance after an ordinary network failure. It revalidates that same live boundary once, holds the foreground mutation gate while local maintenance runs, and never restores or retargets after a failed/stale pass.
- Task, calendar, and week widget refreshes are independent maintenance steps. Each helper preserves `updateAppWidgetState` before Glance `update()`, and an ordinary failure in one family does not suppress the others. Authentication rejection, cancellation, or boundary rejection stops remaining effects.
- Periodic `SyncWorker` work intentionally has no WorkManager network constraint so offline runs can perform local reminder and widget maintenance.
