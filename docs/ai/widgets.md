# Android Glance Widget Rules

Load this for Android widget changes under `androidMain`.

## Location

- Widget code lives under `composeApp/src/androidMain/.../widget/`.
- Widget colors live in `composeApp/src/androidMain/res/values/colors.xml`.

## Glance Rules

- Widget colors must use resource IDs: `ColorProvider(R.color.xxx)`.
- Do not use raw color ints like `ColorProvider(0xFFxxxxxx.toInt())`; they can silently crash widgets.
- Use `actionStartActivity(Intent)` with `ComponentName` for click actions.
- Do not rely on `LocalContext.current` for widget click launch behavior.

## Data Refresh

- Fetch data inside `provideContent` with `produceState` keyed on Glance state.
- Do not capture stale closures from `provideGlance`; it runs once per session.
- Use `updateAppWidgetState` to bump a refresh trigger, then call `update()` to trigger a fresh fetch.
- Use `TaskWidget.refreshWidget()` or `refreshAllWidgets()`.
- Do not call `instance.update()` directly outside widget-owned refresh helpers. The helpers may call `instance.update()` after `updateAppWidgetState`.
